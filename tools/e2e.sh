#!/usr/bin/env bash
# 가상폰: 앱 설치 → 알림 접근 허용 → 광고/일반 알림 보내기 → 광고만 지워졌는지 확인
set -u
OUT=store-assets/e2e; mkdir -p $OUT
PKG=com.jonginzz.adblock
adb install -r ad-blocker-app/app/build/outputs/apk/debug/app-debug.apk
adb shell cmd notification allow_listener $PKG/$PKG.AdBlockService
adb shell dumpsys deviceidle whitelist +$PKG >/dev/null
sleep 3
adb shell am start -n $PKG/.MainActivity; sleep 4
adb shell input keyevent KEYCODE_BACK; sleep 1   # 배터리 안내 창이 뜨면 닫기

post() { adb shell cmd notification post -S bigtext -t "'$2'" "$1" "'$3'"; }
post ad1   "(광고) 오늘만 최대 70% 타임딜" "지금 바로 확인하세요 ※수신거부: 설정"
post ad2   "이벤트 안내"                   "[광고] 첫 주문 5,000원 할인 쿠폰"
post chat1 "엄마"                          "저녁 먹으러 와"
post news1 "네이버경제"                    "프랑스 CAC 40 2% 상승"
sleep 4

adb shell dumpsys notification --noredact > $OUT/dump.txt
has() { grep -q "tag=$1" $OUT/dump.txt && echo 남아있음 || echo 지워짐; }
{
  echo "# 가상폰 테스트 결과 ($(date -u +%F' '%T) UTC, Android 14)"
  echo
  echo "| 보낸 알림 | 기대 | 결과 |"
  echo "|---|---|---|"
  echo "| (광고) 오늘만 최대 70% 타임딜 | 지워짐 | $(has ad1) |"
  echo "| [광고] 첫 주문 5,000원 할인 쿠폰 | 지워짐 | $(has ad2) |"
  echo "| 엄마: 저녁 먹으러 와 | 남아있음 | $(has chat1) |"
  echo "| 네이버경제: CAC 40 상승 | 남아있음 | $(has news1) |"
} > $OUT/RESULT.md
cat $OUT/RESULT.md

# 화면 캡처: 알림창, 앱 화면
adb shell cmd statusbar expand-notifications; sleep 2
adb exec-out screencap -p > $OUT/1-notification-shade.png
adb shell cmd statusbar collapse; sleep 1
adb shell am start -n $PKG/.MainActivity; sleep 3
adb exec-out screencap -p > $OUT/2-app.png
rm -f $OUT/dump.txt
[ "$(has ad1)" = 지워짐 ] && [ "$(has ad2)" = 지워짐 ] && [ "$(has chat1)" = 남아있음 ] && [ "$(has news1)" = 남아있음 ]
