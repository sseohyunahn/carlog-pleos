# CarLog — 차량 공유 인수인계 앱 (v0.3.2)

가족이 함께 쓰는 차에서 주차 위치·차량 상태·전달 메모를 다음 운전자에게 넘겨 주는 Pleos Connect용 Android 앱입니다.

**첫 번째 디자인·음성 ZIP의 기능과 디자인 소스를 기준으로 Android 화면을 구현하고, 두 번째 v0.3 공유 ZIP의 색상을 적용**했습니다. v0.3.2에서는 휴대폰에서 잘리던 화면을 작은 창 전용 배치로 수정했습니다. 기능 코드 19개는 첫 번째 ZIP과 동일합니다. [최신 검증 결과](VALIDATION-v0.3.2.md), [작업 기준과 저장 위치](NATIVE_IMPLEMENTATION.md), [색상·화면 소스](design/README.md)를 참고하세요.

## 기능

- 운전자 선택, 받은 인계와 주차 위치·기록한 사람 표시
- 직접 입력과 10초 음성 메모, 자동 분류·중요 표시, 인계 읽어주기
- 메모 확인과 조치 완료 구분, 완료 취소·작성자 삭제
- 차량 상태·마지막 위치 저장, 지난 주차 위치를 재사용할 때 경고
- 시연/Pleos 데이터 분리, 항목별 연결 진단, 주행 중 입력 제한
- 주간·야간 테마, 반 창 탭과 전체 창 레일, 로컬 기록 저장
- 휴대폰 하단 메뉴와 스크롤 화면, 큰 글씨·가로 회전·소프트 키보드 대응

## 실행과 빌드

JDK 17, Android SDK 35, Gradle Wrapper 8.10.2, Kotlin 2.0.21을 사용합니다. minSdk 28 / targetSdk 34이며 arm64-v8a, x86_64를 지원합니다.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.carlog.app/.MainActivity
.\gradlew.bat connectedDebugAndroidTest
```

첫 실행은 시연 모드입니다. 실제 차량값은 설정에서 Pleos 연결을 선택해 사용합니다. 실제 Pleos 음성 엔진·차량 데이터는 해당 서비스와 권한이 있는 환경이 필요합니다.

release는 `keystore.properties.example`을 `keystore.properties`로 복사해 개인 서명키를 연결한 뒤 빌드합니다.

```powershell
.\gradlew.bat assembleRelease -PcarlogTeam=carlog
```

출력은 `app/build/outputs/apk/release/carlog_CarLog_release.apk`이며 검증한 배포본은 `apk/carlog_CarLog_release.apk`에도 있습니다. 서명키와 비밀번호는 저장소에 포함하지 않습니다. 기존 APK와 키가 다르면 같은 패키지 위에 업데이트 설치할 수 없습니다.

Windows 한글 경로에서 Java 테스트 실행기 문제가 생기면 프로젝트·Gradle 캐시·서명키를 영문 경로로 연결해 빌드하세요. 현재 PC의 재빌드 스크립트는 작업 폴더 루트의 `Build-CarLog-Release.cmd`입니다.

## 소스와 화면

`app/src/main/java/com/carlog/app/ui`에 화면이, `ui/theme/Color.kt`에 실제 색상 배치가 있습니다. 작은 창은 `CompactCarLogApp.kt`를 사용하며 기존 컨트롤러·음성·저장 기능을 공유합니다. 주간·야간 테마는 Android 시스템 설정을 따릅니다. `design/web/screens`는 첫 번째 ZIP의 원본 HTML 구조를 유지하고 `tokens.css`의 색상을 바꿨습니다. `screenshots`는 에뮬레이터에서 캡처한 실제 Android 화면입니다.

Asta Sans는 [Google Fonts 원본](https://github.com/google/fonts/tree/main/ofl/astasans)을 앱에 포함했고, [SIL OFL](licenses/AstaSans-OFL.txt)을 함께 제공합니다. 가변 글꼴 굵기는 [Android 공식 구현 방식](https://developer.android.com/develop/ui/compose/text/fonts#variable-fonts)으로 지정합니다.
