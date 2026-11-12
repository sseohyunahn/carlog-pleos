# Android 구현 검증 — v0.3.1 (2026-11-12)

아래는 v0.3.1 당시의 기록이다. 현재 `apk/carlog_CarLog_release.apk`는 v0.3.2로 교체됐으며 최신 결과는 [VALIDATION-v0.3.2.md](VALIDATION-v0.3.2.md)를 확인한다. v0.3.1 배포 파일은 이 PC의 기존 outputs 폴더에 보존했다.

## 원본 보존 확인

첫 번째 ZIP의 컨트롤러·도메인·데이터베이스·차량 및 음성 플랫폼 Kotlin 파일 **19개가 동일**하다. CR 줄바꿈을 제외한 UTF-8 텍스트를 비교했고, 결과와 각 파일의 SHA-256은 `SOURCE-PROVENANCE.json`에 있다. 화면·테마·아이콘 색상과 차량 화면의 임시 배율만 Android 디자인 적용을 위해 수정했다.

기존 로컬 `carlog_work/CarLog-blue-native`와 원본 ZIP 두 개는 보존했다. 이어받은 작업은 이전 UI 파일의 색상·크기 변경 단계였고, 현재 구현은 `carlog_work/carlog-github`에 있다.

## 빌드 및 테스트

| 검증 | 결과 |
|---|---|
| debug / androidTest / release 빌드 | 성공 |
| 단위 테스트 | **93개 통과, 실패 0** |
| Pleos 에뮬레이터 테스트 | **27개 통과, 실패 0** |
| release 필수 lint 검사 | 통과 |
| release APK 서명 | `apksigner verify` 통과, APK Signature Scheme v2 |
| 실제 release APK 설치·시작 | 성공, `com.carlog.app`, v0.3.1 / versionCode 4 |
| AndroidRuntime 오류 | release 시작 시 치명적 예외 없음 |

환경: 공식 Pleos Android 14/API 34 x86_64 에뮬레이터, 화면 2560×1440, 밀도 160dpi. JDK 17, Gradle 8.10.2, Android SDK 35로 빌드했다.

에뮬레이터 검증에는 운전자 전환 → 받은 인계 → 메모 확인 → 조치 완료 → 완료 취소 → 지난 위치 재사용 경고 → 직접 입력 → 저장 → 다른 운전자에게 전달을 포함한다. DB 보존·삭제·용량 제한 테스트 25개와, 주간/야간·반 창/전체 창·주행 잠금 검사도 포함한다.

주간 전환 후 태그 글자가 사라지는 오프스크린 렌더링 문제를 실제 화면 캡처로 발견해, 같은 구멍 모양을 even-odd 경로로 그리도록 수정했다. 주간 태그의 실제 픽셀 표시를 검사하는 회귀 검증을 추가했다. 테스트 재실행 시 이전 운전자·기록 상태에 영향을 받지 않도록 시연 초기화도 명시했다.

실제 마이크 음질·차량의 Pleos 음성 서버와 센서 연동은 이번 시연 모드 검사 범위에 포함하지 않는다. 해당 기능 코드는 첫 번째 ZIP 그대로이며, 문장 파싱·분류·음성 스크립트는 단위 테스트로 확인했다. `screenshots/`에 실제 Android 렌더링 캡처 8개가 있다.

## APK와 키

- 파일: `apk/carlog_CarLog_release.apk`
- APK SHA-256: `d99eb1dfbcb608fffdbde3d5616a0d647b43ccd0571823d6d272a82972da7921`
- 서명 인증서 SHA-256: `8caa1bfed37f10d4faffbd2e3072a71300dce79d584a209ec004bc3fae038196`
- 키: RSA 3072비트, alias `carlog`, 유효기간 2026-11-12 ~ 2054-03-30

이 PC에서 앞서 생성한 키를 검증해 재사용했다. 개인 키와 비밀번호는 작업 폴더의 `CarLog-release-key`에 보관하며 Git 및 공유 ZIP에서 제외한다.

기존 `main` APK의 인증서 SHA-256은 `3c9f1a104cb5ea5e27b5f188148b0014980130ea1f294aedf7dd742d25e81824`로 다르다. **이전 APK 위에 업데이트 설치할 수 없다.** 기존 앱 삭제 시 로컬 기록도 지워지므로 기록이 필요한 기기는 삭제하지 말고 별도 기기/에뮬레이터에서 확인하거나, 팀의 기존 서명키로 같은 소스를 다시 빌드해야 한다.
