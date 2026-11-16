"""백테스트: python backtest.py  (먼저 collector.py로 데이터를 받아야 합니다)

미래 정보를 미리 보지 않도록, 캔들이 끝난 뒤의 신호로 '다음 캔들 시가'에 체결합니다.
"""
import pandas as pd

import config
import db
from strategy import WARMUP, Portfolio, add_indicators, want_buy, want_sell


def run(df):
    df = add_indicators(df)
    pf = Portfolio(config.INITIAL_CASH)
    trades, curve = [], []
    warmup = WARMUP
    for i in range(warmup, len(df)):
        prev, bar = df.iloc[i - 1], df.iloc[i]
        ts = int(bar["ts"])
        # 1) 직전 캔들 신호 → 이번 캔들 시가에 체결
        if pf.holding and want_sell(prev):
            trades.append(pf.sell(bar["open"], ts, "추세 이탈"))
        elif pf.can_buy(ts) and want_buy(prev):
            trades.append(pf.buy(bar["open"], ts, "상승 추세 진입"))
        # 2) 캔들 도중 손절선 터치 확인
        if pf.holding:
            stop = pf.stop_price()
            if bar["low"] <= stop:
                why = "손절" if stop <= pf.entry else "추적손절(익절)"
                trades.append(pf.sell(min(stop, bar["open"]), ts, why))
            else:
                pf.peak = max(pf.peak, bar["high"])
        curve.append((ts, pf.equity(bar["close"])))
    return trades, curve, df.iloc[warmup:]


def summarize(trades, curve, df):
    final = curve[-1][1]
    peak, mdd = 0, 0
    for _, e in curve:
        peak = max(peak, e)
        mdd = max(mdd, (peak - e) / peak)
    sells = [t for t in trades if t["side"] == "매도"]
    wins = [t for t in sells if t["pnl"] > 0]
    return {
        "전략 수익률": final / config.INITIAL_CASH - 1,
        "그냥 보유(존버) 수익률": df["close"].iloc[-1] / df["open"].iloc[0] - 1,
        "최대 낙폭(MDD)": mdd,
        "매매 횟수": len(sells),
        "승률": len(wins) / len(sells) if sells else 0,
        "가장 큰 1회 손실(원)": min([t["pnl"] for t in sells], default=0),
        "총 수수료(원)": sum(t["fee"] for t in trades),
    }


def yearly(curve, df):
    """연도별 수익률: (연도, 전략, 그냥 보유). 상승장/하락장에서 각각 어땠는지 봅니다."""
    eq = pd.Series([e for _, e in curve],
                   index=pd.to_datetime([t for t, _ in curve], unit="s"))
    px = pd.Series(df["close"].values, index=eq.index)
    out, prev_e, prev_p = [], config.INITIAL_CASH, df["open"].iloc[0]
    for year, e in eq.groupby(eq.index.year):
        p = px[px.index.year == year]
        out.append((year, e.iloc[-1] / prev_e - 1, p.iloc[-1] / prev_p - 1))
        prev_e, prev_p = e.iloc[-1], p.iloc[-1]
    return out


if __name__ == "__main__":
    con = db.connect()
    df = db.load_candles(con)
    if len(df) < WARMUP + 10:
        raise SystemExit("데이터가 부족합니다. 먼저 'python collector.py'를 실행하세요.")
    trades, curve, used = run(df)
    con.execute("DELETE FROM trades WHERE mode='backtest'")
    con.execute("DELETE FROM equity WHERE mode='backtest'")
    for t in trades:
        db.save_trade(con, "backtest", t)
    con.executemany("INSERT OR REPLACE INTO equity VALUES('backtest',?,?)", curve)
    con.commit()
    print(f"기간: 캔들 {len(used)}개 ({config.INTERVAL_MIN}분봉)")
    for k, v in summarize(trades, curve, used).items():
        if "횟수" in k:
            print(f"  {k}: {v}")
        elif "원" in k:
            print(f"  {k}: {v:,.0f}")
        else:
            print(f"  {k}: {v:+.2%}" if "수익률" in k else f"  {k}: {v:.2%}")
    print("연도별 수익률 (전략 / 그냥 보유):")
    for year, a, b in yearly(curve, used):
        print(f"  {year}: {a:+8.2%} / {b:+8.2%}")
