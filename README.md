# 비트코인 자동매매 (1단계: 백테스트 + 모의투자)

업비트 시세로 전략을 검증하고, 실제 돈 없이 실시간 모의투자를 돌리는 프로그램입니다.
**이 단계에는 실제 주문 기능이 없습니다.** API 키도 필요 없습니다.

> 주의: 어떤 전략도 수익을 보장하지 않습니다. 백테스트 수익이 실전 수익을 뜻하지도 않습니다.

## 준비물
1. Python 3.10 이상 — https://www.python.org/downloads/ (설치 시 **Add Python to PATH** 체크)
2. 이 폴더 전체를 노트북에 내려받기

## 실행 순서 (윈도우: 파일 더블클릭)
| 순서 | 파일 | 하는 일 |
|---|---|---|
| 처음 한 번 | `0_install.bat` | 필요한 라이브러리 설치 |
| 1 | `1_backtest.bat` | 업비트 전체 기간(2017년~) 시세 수집 후 전략 검증, 연도별 수익률 출력 (수집 1~3분) |
| 2 | `2_paper_trading.bat` | 실시간 모의투자 시작 (창을 켜 둬야 계속 돕니다) |
| 3 | `3_dashboard.bat` | 수익 확인 창 (브라우저가 열립니다) |

맥/리눅스: `pip install -r requirements.txt` 후 `python collector.py`, `python backtest.py`,
`python paper.py`, `streamlit run dashboard.py`.

## 설정 바꾸기
`config.py`의 숫자만 바꾸면 됩니다 (시작 금액, 손절 비율, 이동평균 기간 등).
전략을 바꾼 뒤에는 `1_backtest.bat`으로 다시 검증하세요.

## 현재 전략
- 60분봉 기준, 단기 이동평균(20)이 장기(60) 위에 있고 RSI가 과열이 아니면 매수
- 추세가 꺾이면 매도, 매수가 -3% 손절, 최고가 -5% 추적손절
- 하루 -3% 손실 시 전량 매도 후 자동 정지 (대시보드에서 재개)

## 파일 구성
`config.py` 설정 · `upbit.py` 시세 조회 · `collector.py` 데이터 수집 · `strategy.py` 전략/가상계좌 ·
`backtest.py` 백테스트 · `paper.py` 모의투자 · `dashboard.py` 수익 확인 창 · `db.py` 저장소

## 다음 단계 (예정)
뉴스 수집 + AI 분석, 시장 심리 지표, 업비트 실전 주문.
