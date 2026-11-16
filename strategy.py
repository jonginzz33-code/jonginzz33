"""전략과 가상 계좌. 백테스트와 모의투자가 같은 코드를 씁니다."""
import config

WARMUP = 2 * max(config.EMA_SLOW, config.EMA_TREND)   # 지표가 안정되는 데 필요한 캔들 수


def add_indicators(df):
    df = df.copy()
    df["ema_fast"] = df["close"].ewm(span=config.EMA_FAST, adjust=False).mean()
    df["ema_slow"] = df["close"].ewm(span=config.EMA_SLOW, adjust=False).mean()
    df["ema_trend"] = df["close"].ewm(span=config.EMA_TREND, adjust=False).mean()
    delta = df["close"].diff()
    gain = delta.clip(lower=0).ewm(alpha=1 / config.RSI_PERIOD, adjust=False).mean()
    loss = (-delta.clip(upper=0)).ewm(alpha=1 / config.RSI_PERIOD, adjust=False).mean()
    df["rsi"] = 100 - 100 / (1 + gain / loss.replace(0, 1e-12))
    return df


def want_buy(row):
    """큰 추세가 상승이고, 단기 추세도 상승이며, 과열이 아닐 때만 매수."""
    return (row["close"] > row["ema_trend"]
            and row["ema_fast"] > row["ema_slow"] and row["close"] > row["ema_slow"]
            and row["rsi"] < config.RSI_MAX_ENTRY)


def want_sell(row):
    """추세가 꺾이면(단기선 < 장기선) 매도."""
    return row["ema_fast"] < row["ema_slow"]


class Portfolio:
    """현금과 코인 보유량을 관리하는 가상 계좌."""

    def __init__(self, cash, qty=0.0, cost=0.0, entry=0.0, peak=0.0,
                 cooldown_until=0):
        self.cash, self.qty, self.cost = cash, qty, cost
        self.entry, self.peak = entry, peak
        self.cooldown_until = cooldown_until

    def can_buy(self, ts):
        """보유 중이 아니고, 손절 후 쉬는 시간이 지났을 때만 매수 가능."""
        return not self.holding and ts >= self.cooldown_until

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
        # 손절당해도 자산의 RISK_PER_TRADE만 잃도록 매수 금액을 계산
        fraction = min(config.MAX_POSITION_FRACTION,
                       config.RISK_PER_TRADE / config.STOP_LOSS)
        krw = self.cash * fraction
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
        if pnl < 0:                       # 손실로 끝났으면 한동안 쉬어 감
            self.cooldown_until = ts + config.COOLDOWN_CANDLES * config.INTERVAL_MIN * 60
        self.qty = self.cost = self.entry = self.peak = 0.0
        return trade
