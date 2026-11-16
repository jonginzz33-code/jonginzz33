"""SQLite 저장소. 캔들, 매매내역, 자산곡선, 봇 상태를 파일 하나에 보관합니다."""
import sqlite3
from pathlib import Path

import pandas as pd

import config

SCHEMA = """
CREATE TABLE IF NOT EXISTS candles(
  market TEXT, interval INTEGER, ts INTEGER,
  open REAL, high REAL, low REAL, close REAL, volume REAL,
  PRIMARY KEY(market, interval, ts));
CREATE TABLE IF NOT EXISTS trades(
  id INTEGER PRIMARY KEY AUTOINCREMENT, mode TEXT, ts INTEGER, side TEXT,
  price REAL, qty REAL, fee REAL, krw REAL, pnl REAL, reason TEXT);
CREATE TABLE IF NOT EXISTS equity(
  mode TEXT, ts INTEGER, equity REAL, PRIMARY KEY(mode, ts));
CREATE TABLE IF NOT EXISTS state(key TEXT PRIMARY KEY, value TEXT);
"""


def connect():
    Path(config.DB_PATH).parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(config.DB_PATH, timeout=30)
    con.execute("PRAGMA journal_mode=WAL")
    con.executescript(SCHEMA)
    return con


def save_candles(con, rows):
    """rows: (ts, open, high, low, close, volume) 목록"""
    con.executemany(
        "INSERT OR REPLACE INTO candles VALUES(?,?,?,?,?,?,?,?)",
        [(config.MARKET, config.INTERVAL_MIN, *r) for r in rows])
    con.commit()


def load_candles(con, limit=None):
    q = ("SELECT ts, open, high, low, close, volume FROM candles "
         "WHERE market=? AND interval=? ORDER BY ts DESC")
    if limit:
        q += f" LIMIT {int(limit)}"
    df = pd.read_sql(q, con, params=(config.MARKET, config.INTERVAL_MIN))
    return df.iloc[::-1].reset_index(drop=True)


def get_state(con, key, default=None):
    row = con.execute("SELECT value FROM state WHERE key=?", (key,)).fetchone()
    return row[0] if row else default


def set_state(con, key, value):
    con.execute("INSERT OR REPLACE INTO state VALUES(?,?)", (key, str(value)))
    con.commit()


def save_trade(con, mode, t):
    con.execute(
        "INSERT INTO trades(mode, ts, side, price, qty, fee, krw, pnl, reason) "
        "VALUES(?,?,?,?,?,?,?,?,?)",
        (mode, t["ts"], t["side"], t["price"], t["qty"], t["fee"], t["krw"],
         t["pnl"], t["reason"]))
    con.commit()


def save_equity(con, mode, ts, equity):
    con.execute("INSERT OR REPLACE INTO equity VALUES(?,?,?)", (mode, ts, equity))
    con.commit()
