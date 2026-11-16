"""과거 캔들 수집: python collector.py"""
import time

import config
import db
import upbit


def collect_history(con, total=config.HISTORY_CANDLES):
    """과거로 거슬러 올라가며 total개가 될 때까지 저장합니다."""
    to_ts, got = None, 0
    while got < total:
        rows = upbit.fetch_candles(200, to_ts)
        if not rows:
            break
        db.save_candles(con, rows)
        got += len(rows)
        to_ts = rows[0][0]                # 가장 오래된 캔들 이전부터 다시 요청
        print(f"\r수집 중... {got}/{total}", end="", flush=True)
        time.sleep(0.15)
    print()


def update_recent(con):
    """최근 200개를 갱신합니다 (모의투자 루프에서 사용)."""
    db.save_candles(con, upbit.fetch_candles(200))


if __name__ == "__main__":
    con = db.connect()
    collect_history(con)
    n = con.execute("SELECT COUNT(*) FROM candles").fetchone()[0]
    print(f"완료: 캔들 {n}개 저장됨 ({config.DB_PATH})")
