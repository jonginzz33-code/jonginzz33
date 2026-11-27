"""Google Play 내부 테스트 자동 업로드.

환경변수 PLAY_SA_JSON(서비스 계정 JSON 내용)이 있어야 동작한다.
하는 일: AAB 업로드 → 스토어 등록정보(글/아이콘/그래픽/스크린샷) → 내부 테스트 트랙 출시.
"""
import glob
import json
import os
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

PKG = "com.jonginzz.adblock"
DIR = "play-upload"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG}/edits"
UP = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PKG}/edits"


def session():
    raw = os.environ.get("PLAY_SA_JSON", "").strip()
    if not raw:
        print("::warning::PLAY_SA_JSON 비밀값이 없어 업로드를 건너뜁니다. (설정 후 다시 실행)")
        sys.exit(0)
    creds = service_account.Credentials.from_service_account_info(
        json.loads(raw), scopes=["https://www.googleapis.com/auth/androidpublisher"])
    creds.refresh(Request())
    s = requests.Session()
    s.headers["Authorization"] = f"Bearer {creds.token}"
    return s


def ok(r):
    if r.status_code >= 400:
        raise RuntimeError(f"{r.request.method} {r.url.split('?')[0]} → {r.status_code}\n{r.text}")
    return r.json() if r.text else {}


def run(s, status):
    listing = json.load(open(f"{DIR}/listing.json", encoding="utf-8"))
    lang = listing["language"]
    edit = ok(s.post(API))["id"]
    print("edit", edit)

    aab = sorted(glob.glob(f"{DIR}/*.aab"))[-1]
    print("AAB 업로드:", aab)
    with open(aab, "rb") as f:
        vc = ok(s.post(f"{UP}/{edit}/bundles?uploadType=media", data=f,
                       headers={"Content-Type": "application/octet-stream"}, timeout=600))["versionCode"]
    print("versionCode", vc)

    print("스토어 등록정보 글")
    ok(s.put(f"{API}/{edit}/listings/{lang}", json={
        "language": lang, "title": listing["title"],
        "shortDescription": listing["shortDescription"], "fullDescription": listing["fullDescription"]}))

    images = {"icon": [f"{DIR}/icon-512.png"],
              "featureGraphic": [f"{DIR}/feature-1024x500.png"],
              "phoneScreenshots": sorted(glob.glob(f"{DIR}/screenshots/*.png") + glob.glob(f"{DIR}/screenshots/*.jpg"))}
    for kind, files in images.items():
        files = [p for p in files if os.path.exists(p)]
        if not files:
            print(f"  {kind}: 파일 없음, 건너뜀")
            continue
        ok(s.delete(f"{API}/{edit}/listings/{lang}/{kind}"))
        for p in files:
            ctype = "image/png" if p.endswith(".png") else "image/jpeg"
            with open(p, "rb") as f:
                ok(s.post(f"{UP}/{edit}/listings/{lang}/{kind}?uploadType=media", data=f,
                          headers={"Content-Type": ctype}))
        print(f"  {kind}: {len(files)}장")

    print("내부 테스트 트랙:", status)
    ok(s.put(f"{API}/{edit}/tracks/internal", json={"track": "internal", "releases": [{
        "versionCodes": [str(vc)], "status": status,
        "releaseNotes": [{"language": lang, "text": listing["releaseNotes"]}]}]}))
    ok(s.post(f"{API}/{edit}:commit"))
    print(f"✓ 완료 (status={status})")


def main():
    s = session()
    try:
        run(s, "completed")
    except RuntimeError as e:
        # 한 번도 출시 안 된 새 앱은 'draft'로만 만들 수 있다 → Play Console에서 '출시' 한 번 눌러야 함
        # 403 PERMISSION_DENIED 로 오는 경우도 있어 오류가 나면 초안으로 한 번 더 시도
        print(f"completed 실패 → 초안(draft)으로 다시 올립니다.\n{e}")
        if True:
            run(s, "draft")
            print("::notice::Play Console → 내부 테스트 → 초안 버전에서 '출시 검토 → 출시 시작'을 눌러 주세요.")
        else:
            raise


if __name__ == "__main__":
    main()
