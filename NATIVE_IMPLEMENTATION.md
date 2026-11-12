# Android 구현 기준과 저장 위치 (2026-11-12)

이번 브랜치는 `halfsamoan/carlog`의 `main` 커밋 `88a8b43`에서 시작했다.

- 기능 원본: `CarLog_디자인_음성_20261108.zip`의 `carlog-v2/app/src`.
- 디자인 원본: 같은 ZIP의 `design/web`, `design/compose`, `design/tokens`.
- 색상 원본: `CarLog-v0.3-share.zip`의 `CarLog-source/app/src/main/java/com/carlog/app/ui/Common.kt`.
- 이어받은 로컬 작업: `carlog_work/CarLog-blue-native`의 UI 파일 5개. 이 폴더는 수정하거나 삭제하지 않았다.

시작 시점에 기존 폴더에는 파란색 치환과 일부 글자·여백 변경만 저장돼 있었다. `ui/theme` 연결, 태그의 구멍·절취선, Asta Sans 폰트, 새 작업에 대한 APK 출력과 테스트 결과는 없었다. 이 브랜치에서 해당 Android 구현을 이어갔다.

음성 받아쓰기·자동 분류·음성 읽기, 운전자별 인계, 확인과 조치 완료 구분, 위치 재사용 경고, 차량 데이터·연결 진단, 주행 중 입력 제한은 첫 번째 ZIP의 코드를 사용한다. 두 번째 ZIP의 검색·필터 기능이나 화면 구성은 합치지 않는다. 기존 저장/음성 입력 흐름도 유지한다.

색은 `app/src/main/java/com/carlog/app/ui/theme/Color.kt`에서 관리한다. 야간 바탕 `#091522`, 패널 `#142638`, 강조 `#8ACFFF`; 주간 바탕 `#F2F5F8`, 강조 `#0057B8`; 인계 태그는 두 테마 모두 `#002C5F`다. 주간/야간은 Android 시스템 설정을 따른다. 글자·모서리·버튼 크기와 태그 부품은 첫 번째 디자인 소스에 기반한다. 폰트는 앱에 포함되므로 실행 중 다운로드하지 않는다.

차량 화면에 사용하던 임시 1.35배 확대는 제거했다. 첫 디자인의 크기 토큰이 이미 차량 dp 기준이기 때문이다. 반 창은 1060dp 탭, 전체 창은 1600dp 이상에서 320dp 레일과 두 열 배치를 사용한다.

## 빌드

JDK 17, Android SDK 35, Gradle 8.10.2를 사용한다.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest
.\gradlew.bat assembleRelease -PcarlogTeam=carlog
```

release 서명은 프로젝트 루트의 `keystore.properties`에서 읽는다. 예제 파일을 복사한 뒤 본인 키 경로와 비밀번호를 입력한다. 실제 키, 비밀번호, `local.properties`, 빌드 캐시는 Git에 포함하지 않는다. 한국어 경로에서 Java 17 테스트 실행기 문제가 생기는 이 PC에서는 프로젝트와 Gradle 캐시를 영문 경로의 junction으로 연결해 빌드했다.

최초 구현은 `feat/android-blue-design`에 저장했다. 이후 휴대폰 검증·수정본 v0.3.2는 로컬 `fix/phone-validation`에서 작업했다. 이번 검증에서 GitHub 상태 확인이나 업로드는 하지 않았다. 최신 결과와 APK 체크섬은 `VALIDATION-v0.3.2.md`, v0.3.1 당시 결과는 `VALIDATION-NATIVE.md`에 기록한다.

## 휴대폰 대응 (v0.3.2)

창 너비가 840dp 미만이거나 높이가 600dp 미만이면 `CompactCarLogApp.kt`의 작은 창 배치를 사용한다. 헤더·하단 메뉴와 스크롤 본문으로 구성하고 시스템 글꼴 배율을 유지한다. 큰 차량 창의 기존 화면과 파랑·네이비 팔레트, 컨트롤러·저장·음성·차량 연결 코드는 유지했다.

SDK의 manifest 병합 과정에서 자동차 기능이 필수로 되돌아가던 문제를 `tools:replace="android:required"`로 수정했다. 최종 APK는 자동차 기능과 `android.car` 라이브러리가 선택 사항이며 일반 휴대폰에서도 설치·실행할 수 있다. 지원 기준은 Android 9 이상, arm64-v8a 또는 x86_64이다.
