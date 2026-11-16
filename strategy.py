"""전략과 가상 계좌. 백테스트와 모의투자가 같은 코드를 씁니다."""
import config


def add_indicators(df):
    df = df.copy()
    df["ema_fast"] = df["close"].ewm(span=config.EMA_FAST, adjust=False).mean()
    df["ema_slow"] = df["close"].ewm(span=config.EMA_SLOW, adjust=False).mean()
    delta = df["close"].diff()
    gain = delta.clip(lower=0).ewm(alpha=1 / config.RSI_PERIOD, adjust=False).mean()
    loss = (-delta.clip(upper=0)).ewm(alpha=1 / config.RSI_PERIOD, adjust=False).mean()
    df["rsi"] = 100 - 100 / (1 + gain / loss.replace(0, 1e-12))
    return df


def want_buy(row):
    """상승 추세(단기선 > 장기선)이고 과열이 아닐 때 매수."""
    return (row["ema_fast"] > row["ema_slow"] and row["close"] > row["ema_slow"]
            and row["rsi"] < config.RSI_MAX_ENTRY)


def want_sell(row):
    """추세가 꺾이면(단기선 < 장기선) 매도."""
    return row["ema_fast"] < row["ema_slow"]


class Portfolio:
    """현금과 코인 보유량을 관리하는 가상 계좌."""

    def __init__(self, cash, qty=0.0, cost=0.0, entry=0.0, peak=0.0):
        self.cash, self.qty, self.cost = cash, qty, cost
        self.entry, self.peak = entry, peak

    @property
    def holding(self):
        return self.qty > 0

    def equity(self, price):
        return self.cash + self.qty * price

    def stop_price(self):
        """손절선과 추적손절선 중 높은 쪽."""
        return max(self.entry * (1 - config.STOP_LOSS),
                   self.peak * (1 - config.TRAILING_STOP))

    def buy(self, price, ts, reason):
        krw = self.cash * config.POSITION_FRACTION
        fill = price * (1 + config.SLIPPAGE)
        fee = krw * config.FEE_RATE
        self.qty = (krw - fee) / fill
        self.cash -= krw
        self.cost, self.entry, self.peak = krw, fill, fill
        return dict(ts=ts, side="매수", price=fill, qty=self.qty, fee=fee,
                    krw=krw, pnl=0.0, reason=reason)

    def sell(self, price, ts, reason):
        fill = price * (1 - config.SLIPPAGE)
        gross = self.qty * fill
        fee = gross * config.FEE_RATE
        pnl = gross - fee - self.cost
        trade = dict(ts=ts, side="매도", price=fill, qty=self.qty, fee=fee,
                     krw=gross - fee, pnl=pnl, reason=reason)
        self.cash += gross - fee
        self.qty = self.cost = self.entry = self.peak = 0.0
        return trade
