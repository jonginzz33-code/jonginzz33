"""모의투자: python paper.py   (실제 주문은 전혀 나가지 않습니다)

실시간 업비트 시세로 가상 계좌를 굴립니다. 껐다 켜도 이어서 진행됩니다.
"""
import json
import time
from datetime import datetime, timedelta, timezone

import config
import db
import upbit
from collector import collect_history, update_recent
from strategy import WARMUP, Portfolio, add_indicators, want_buy, want_sell

MODE = "paper"
KST = timezone(timedelta(hours=9))


def load_portfolio(con):
    raw = db.get_state(con, "portfolio")
    return Portfolio(**json.loads(raw)) if raw else Portfolio(config.INITIAL_CASH)


def save_portfolio(con, pf):
    db.set_state(con, "portfolio", json.dumps(vars(pf)))


def record(con, trade):
    db.save_trade(con, MODE, trade)
    print(f"[{datetime.now(KST):%m-%d %H:%M}] {trade['side']} "
          f"{trade['price']:,.0f}원 ({trade['reason']}) 손익 {trade['pnl']:+,.0f}원")


def step(con, pf):
    now = int(time.time())
    price = upbit.fetch_price()
    equity = pf.equity(price)

    # 하루 손실 한도: 한국시간 날짜가 바뀌면 기준 자산을 새로 잡음
    today = datetime.now(KST).strftime("%Y-%m-%d")
    if db.get_state(con, "day") != today:
        db.set_state(con, "day", today)
        db.set_state(con, "day_start_equity", equity)
    day_start = float(db.get_state(con, "day_start_equity", equity))
    if equity < day_start * (1 - config.DAILY_LOSS_LIMIT):
        if pf.holding:
            record(con, pf.sell(price, now, "하루 손실 한도 도달"))
        db.set_state(con, "paused", 1)
        db.set_state(con, "pause_reason", "하루 손실 한도 도달 - 대시보드에서 재개하세요")

    paused = db.get_state(con, "paused", "0") == "1"

    # 손절은 정지 상태에서도 항상 작동
    if pf.holding:
        stop = pf.stop_price()
        if price <= stop:
            record(con, pf.sell(price, now,
                                "손절" if stop <= pf.entry else "추적손절(익절)"))
        else:
            pf.peak = max(pf.peak, price)

    # 새 캔들이 완성됐을 때만 매수/매도 신호 판단
    update_recent(con)
    df = add_indicators(db.load_candles(con, limit=WARMUP + 50))
    closed = df[df["ts"] + config.INTERVAL_MIN * 60 <= now]
    if len(closed) >= WARMUP:
        last = closed.iloc[-1]
        if str(int(last["ts"])) != db.get_state(con, "last_candle"):
            db.set_state(con, "last_candle", int(last["ts"]))
            if pf.holding and want_sell(last):
                record(con, pf.sell(price, now, "추세 이탈"))
            elif pf.can_buy(now) and not paused and want_buy(last):
                record(con, pf.buy(price, now, "상승 추세 진입"))

    save_portfolio(con, pf)
    db.save_equity(con, MODE, now, pf.equity(price))
    db.set_state(con, "last_price", price)
    db.set_state(con, "heartbeat", now)


def main():
    con = db.connect()
    pf = load_portfolio(con)
    print("최근 시세 불러오는 중...")
    collect_history(con, WARMUP + 200)      # 꺼져 있던 동안의 캔들을 채움
    print(f"모의투자 시작 ({config.MARKET}, {config.INTERVAL_MIN}분봉). 종료: Ctrl+C")
    while True:
        try:
            step(con, pf)
        except Exception as e:            # 인터넷 끊김 등 → 다음 주기에 재시도
            print(f"오류(재시도 예정): {e}")
        time.sleep(config.LOOP_SECONDS)


if __name__ == "__main__":
    main()
