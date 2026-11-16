"""수익 확인 창: streamlit run dashboard.py"""
import json
import time
from datetime import timedelta, timezone

import pandas as pd
import streamlit as st

import config
import db

KST = timezone(timedelta(hours=9))
st.set_page_config(page_title="비트코인 자동매매", page_icon="📈", layout="wide")
st.title("📈 비트코인 자동매매 현황")

label = st.radio("보기", ["모의투자 (실시간)", "백테스트 (과거 검증)"], horizontal=True)
mode = "paper" if label.startswith("모의") else "backtest"


def to_kst(series):
    return pd.to_datetime(series, unit="s", utc=True).dt.tz_convert(KST)


@st.fragment(run_every="10s")
def view():
    con = db.connect()
    eq = pd.read_sql("SELECT ts, equity FROM equity WHERE mode=? ORDER BY ts",
                     con, params=(mode,))
    trades = pd.read_sql("SELECT * FROM trades WHERE mode=? ORDER BY ts DESC",
                         con, params=(mode,))
    if eq.empty:
        st.info("아직 데이터가 없습니다. "
                + ("'python paper.py'를 실행하세요." if mode == "paper"
                   else "'python collector.py' 후 'python backtest.py'를 실행하세요."))
        return

    equity = eq["equity"].iloc[-1]
    profit = equity - config.INITIAL_CASH
    sells = trades[trades["side"] == "매도"]
    c1, c2, c3, c4 = st.columns(4)
    c1.metric("총 자산", f"{equity:,.0f}원")
    c2.metric("총 수익", f"{profit:+,.0f}원", f"{profit / config.INITIAL_CASH:+.2%}")
    c3.metric("매매 횟수", f"{len(sells)}회")
    c4.metric("승률", f"{(sells['pnl'] > 0).mean():.0%}" if len(sells) else "-")

    if mode == "paper":
        beat = int(db.get_state(con, "heartbeat", 0))
        paused = db.get_state(con, "paused", "0") == "1"
        running = time.time() - beat < config.LOOP_SECONDS * 4
        pf = json.loads(db.get_state(con, "portfolio", "{}"))
        price = float(db.get_state(con, "last_price", 0))
        day_start = float(db.get_state(con, "day_start_equity", equity))

        a, b, c = st.columns(3)
        a.metric("오늘 손익", f"{equity - day_start:+,.0f}원",
                 f"{equity / day_start - 1:+.2%}")
        b.metric("현재가", f"{price:,.0f}원")
        if pf.get("qty", 0) > 0:
            c.metric("보유 중", f"{pf['qty']:.6f} BTC",
                     f"{price / pf['entry'] - 1:+.2%} (매수가 {pf['entry']:,.0f}원)")
        else:
            c.metric("보유 중", "없음 (현금 대기)")

        if not running:
            st.error("⚠️ 봇이 꺼져 있습니다. 'python paper.py'를 실행하세요.")
        elif paused:
            st.warning("⏸ 신규 매수 정지 중: "
                       + db.get_state(con, "pause_reason", "수동 정지"))
        else:
            st.success("✅ 봇 실행 중")

        if paused:
            if st.button("▶ 매매 재개"):
                db.set_state(con, "paused", 0)
                st.rerun()
        elif st.button("🛑 긴급 정지 (신규 매수 중단)", type="primary"):
            db.set_state(con, "paused", 1)
            db.set_state(con, "pause_reason", "수동 정지")
            st.rerun()

    st.subheader("자산 변화")
    chart = eq.assign(시간=to_kst(eq["ts"])).set_index("시간")[["equity"]]
    if len(chart) > 2000:                       # 점이 너무 많으면 간격을 띄워 그림
        chart = chart.iloc[::len(chart) // 2000]
    st.line_chart(chart.rename(columns={"equity": "자산(원)"}))

    st.subheader("매매 내역")
    if trades.empty:
        st.write("아직 매매가 없습니다.")
    else:
        t = trades.assign(시간=to_kst(trades["ts"]).dt.strftime("%Y-%m-%d %H:%M"))
        t = t[["시간", "side", "price", "krw", "pnl", "reason"]]
        t.columns = ["시간", "구분", "체결가(원)", "금액(원)", "손익(원)", "이유"]
        st.dataframe(t.style.format({"체결가(원)": "{:,.0f}", "금액(원)": "{:,.0f}",
                                     "손익(원)": "{:+,.0f}"}),
                     hide_index=True, width="stretch")


view()
