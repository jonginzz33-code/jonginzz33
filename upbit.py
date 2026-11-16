"""업비트 공개 시세 API (API 키 불필요, 주문 기능 없음)."""
import time
from datetime import datetime, timezone

import requests

import config

BASE = "https://api.upbit.com/v1"


def _get(path, params):
    for attempt in range(5):
        r = requests.get(BASE + path, params=params, timeout=10)
        if r.status_code == 429:          # 요청 과다 → 잠시 대기 후 재시도
            time.sleep(1 + attempt)
            continue
        r.raise_for_status()
        return r.json()
    raise RuntimeError("업비트 API 요청 제한에 계속 걸립니다. 잠시 후 다시 시도하세요.")


def _to_row(c):
    ts = int(datetime.fromisoformat(c["candle_date_time_utc"])
             .replace(tzinfo=timezone.utc).timestamp())
    return (ts, c["opening_price"], c["high_price"], c["low_price"],
            c["trade_price"], c["candle_acc_trade_volume"])


def fetch_candles(count=200, to_ts=None):
    """최신(또는 to_ts 이전) 캔들을 최대 200개 가져옵니다. 오래된 순으로 반환."""
    params = {"market": config.MARKET, "count": min(count, 200)}
    if to_ts:
        params["to"] = datetime.fromtimestamp(to_ts, timezone.utc).strftime(
            "%Y-%m-%d %H:%M:%S")
    data = _get(f"/candles/minutes/{config.INTERVAL_MIN}", params)
    return sorted(_to_row(c) for c in data)


def fetch_price():
    """현재가(원)."""
    return float(_get("/ticker", {"markets": config.MARKET})[0]["trade_price"])
