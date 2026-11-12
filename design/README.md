# CarLog Android 디자인 소스

첫 번째 ZIP의 화면·문구·부품 구조를 유지하고 두 번째 ZIP의 색상만 적용한 소스입니다.

- `web/screens`: 원본 HTML 화면.
- `web/tokens.css`, `tokens/carlog-tokens.json`: 파랑·네이비 색상과 원래 크기 토큰.
- `compose`: Android에서 사용하는 테마 소스의 복사본. 실제 앱 수정은 `app/src/main/java/com/carlog/app/ui`에서 합니다.
- `README-original.md`, `compose/MIGRATION.md`: 받은 원본 설명입니다. 이전 Sand 색상에 대한 설명은 현재 팔레트와 다릅니다.
- 검증한 실제 앱 화면은 저장소 루트 `screenshots/`를 확인하세요.

작은 휴대폰 창은 기존 기능과 팔레트를 공유하는 `CompactCarLogApp.kt`로 대응합니다. 웹 디자인 참고 화면은 기존 차량 구성을 유지합니다.

APK 동작과 최신 검증 범위는 `../NATIVE_IMPLEMENTATION.md`, `../VALIDATION-v0.3.2.md`에 기록했습니다.
