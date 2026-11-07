# CarLog 차량 공유 인수인계 프로토타입

현대자동차그룹 차량용 앱 개발 공모전을 위한 Kotlin Android 앱입니다. 차량을 함께 사용하는 가족이 인계 카드를 남기고, 다음 운전자가 확인한 뒤 실제 조치를 완료하는 흐름을 구현했습니다.

## 바로 시작하기와 참여

- [설치용 APK 다운로드](https://github.com/sseohyunahn/carlog-pleos/releases/download/v0.1.0/CarLog-prototype.apk): 앱을 먼저 확인하고 싶을 때 사용합니다. 다른 버전은 [전체 릴리스 목록](https://github.com/sseohyunahn/carlog-pleos/releases)에서 확인할 수 있습니다.
- [Fork해서 참여하기](https://github.com/sseohyunahn/carlog-pleos/fork): 본인 저장소에서 수정한 뒤 이 저장소로 PR을 보낼 수 있습니다.
- [개선 작업과 버그 목록](https://github.com/sseohyunahn/carlog-pleos/issues): 함께 진행할 작업을 선택하거나 새 문제를 등록합니다.
- [협업 안내](CONTRIBUTING.md): 개발 환경, 작업 순서, 확인 방법을 설명합니다.
- [우선 개선 작업](ROADMAP.md): 코드 검토에서 발견한 보완점과 완료 조건을 정리했습니다.

공개 저장소의 Fork·PR 참여에는 별도 초대가 필요하지 않습니다. 원본 저장소에 직접 push하려면 저장소 관리자의 협업자 초대가 필요합니다.

## 실행

아래 과정은 저장소를 처음 내려받는 참여자를 위한 안내입니다. 기존 개발 PC에 별도로 제공된 `CarLog-run.cmd`는 해당 PC에 설치한 개발 도구를 사용하는 실행 보조 파일이며, GitHub 저장소에는 포함되지 않습니다.

1. Android Studio에서 이 폴더를 프로젝트로 엽니다.
2. JDK 17, Android SDK Platform 35, Build Tools 35.0.0을 준비합니다. Gradle Wrapper 버전은 8.10.2입니다.
3. 공식 가이드에 따라 Pleos Connect 2.1.0 에뮬레이터(API 34)를 생성하고 Cold Boot로 실행합니다.
4. `app`을 실행하거나 제공된 `CarLog-prototype.apk`를 설치합니다. 첫 실행은 가상 데이터로 동작하는 **시연 모드**입니다.
5. 설정에서 **Pleos 연결**을 선택하고 동의 및 위치 권한을 허용하면 해당 에뮬레이터의 제공 데이터를 조회합니다.

Windows에서는 SDK와 Gradle 캐시 경로에 영문 경로를 사용하는 것을 권장합니다. 이 프로젝트는 한글 프로젝트 경로 검사에 대한 AGP 옵션을 포함합니다. 환경에 따라 네이티브 빌드 도구나 테스트 실행기에서 경로 문제가 생기면 프로젝트도 영문 경로에 풀어 주세요. 배포 ZIP에는 개발자 PC의 `local.properties`, 빌드 캐시, SDK, 가상 기기 이미지, 서명 키가 포함되지 않습니다.

SDK 위치는 각자 Android Studio에서 설정합니다. `local.properties`나 다른 개발자의 SDK 경로를 복사해 Git에 올리지 않습니다. 자세한 첫 실행 절차는 [협업 안내](CONTRIBUTING.md)를 확인하세요.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat connectedDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.carlog.app/.MainActivity
```

## 2분 시연 순서

1. 기본 프로필 **어머니**에서 아버지가 남긴 충전 확인과 우산 메모를 확인합니다.
2. **확인했어요**를 눌러도 해당 메모가 미처리 상태로 남는 것을 보여 줍니다.
3. 실제 조치를 가정하여 **조치 완료**를 누릅니다. 지난 기록에서 작성자, 완료한 사람, 완료 시각을 확인하고 **완료 취소**도 할 수 있습니다.
4. **인계 남기기**에서 주차 구역과 물품 메모를 작성합니다. 수신 대상을 모두 또는 특정 프로필로 선택하고 저장합니다.
5. 상단 프로필을 **아버지**로 바꾸면 어머니가 남긴 카드가 인계 보드에 나타납니다. 앱을 종료하고 다시 실행해도 저장 기록이 유지됩니다.
6. 설정의 **GPS 음영 상황**과 **주행 중 입력 잠금 시연**으로 예외 처리를 보여 줍니다.
7. 설정의 **처음 시연 상황으로 복원**으로 샘플을 다시 준비할 수 있습니다.

## 구현된 기능

- 가족 3개 공유 프로필과 수신 대상 지정
- 주차 층·구역 직접 입력, 조회 시각 및 차량 상태 스냅샷 저장
- 한 인계 카드에 최대 10개 메모, 분류와 중요 표시
- 읽음 확인과 조치 완료를 별도로 저장, 완료자의 되돌리기
- 시연/실차 연결 모드별 데이터베이스 분리
- 최신 조회가 2분 이상 지났을 때 재조회 후 저장 안내
- ON_DEVICE Pleos 음성 입력·읽어주기 연동, 시연 모드에서 Android 음성 엔진 사용
- 실제 위치 권한 요청, SDK 미지원·응답 없음·오래된 위치 처리
- CarUxRestrictions에 따른 입력 차단 및 앱 중지 시 음성 서비스 정리
- 탭 이동 중 작성 내용 유지, 앱 내부 저장, 사용자 요청 시 전체 기록 삭제
- Android 백업 및 기기 이전으로 인계 데이터가 복제되지 않도록 설정

## SDK 및 환경

| 항목 | 설정 |
|---|---|
| 언어 / 화면 | Kotlin 2.0.21 / Jetpack Compose |
| Android | minSdk 28 / compileSdk 35 / targetSdk 34 |
| 빌드 | AGP 8.8.2 / Gradle 8.10.2 / JDK 17 |
| 차량 | `ai.pleos.playground:Vehicle:2.3.2` |
| 위치 | `ai.pleos.playground:FusedLocation:1.1.3` |
| 음성 입력 / 출력 | `SpeechToText:3.3.1` / `TextToSpeech:3.3.1` |
| 저장 | Android SQLiteOpenHelper, 트랜잭션 및 외래키 |

FusedLocation 문서 예시의 1.1.2는 확인 당시 Maven 저장소에서 제공되지 않아 실제 제공되는 1.1.3을 사용합니다. 저장 방식은 기획서의 Room 대신 프로토타입 의존성을 줄인 Android 기본 SQLite를 사용했습니다.

차량 데이터는 capability의 읽기 지원을 확인한 뒤 공개 API로 조회합니다. 배터리 잔량, 연료 비율, 누적 주행거리, 워셔액 부족 경고를 지원하며 미지원 값은 빈 값과 진단 사유로 남깁니다. 차량 설정을 변경하는 API는 호출하지 않습니다. 위치의 confidence는 SDK 원시값이며 미터 단위 정확도로 해석하지 않습니다.

차량의 공개 CarUxRestrictionsManager API는 선택적 `android.car` 런타임 연동으로 사용합니다. 일반 Android 시연 기기에서도 APK를 설치할 수 있게 클래스 로딩을 분리했습니다. 확인 불가 시 Pleos 모드의 입력은 잠깁니다. 앱은 distractionOptimized를 선언하지 않은 정차용 앱입니다.

## 프로토타입의 범위

- **탑승 전 모바일 위치 공유는 미구현**입니다. 차량 내 주차 기록 확인만 가능하며 차 밖에서 차를 찾게 해 준다고 주장하지 않습니다.
- **시동 OFF·운전자 교대의 완전 자동 감지는 미구현**입니다. 화면 사용 제한 해제를 정차 후보로 안내하고 사용자가 인계 카드를 확정합니다. 앱 종료 상태의 백그라운드 수집도 하지 않습니다.
- 프로필은 수신 대상을 구분하는 공유 프로필입니다. 비밀번호·생체 인증·차량 소유권 확인에 따른 보안 경계가 아닙니다.
- Pleos ON_DEVICE 음성은 차량의 서비스, 권한, 한국어 모델과 마이크 지원이 필요합니다. 실제 한국어 음성 인식 품질은 별도 검증해야 합니다. 서비스가 없으면 글 입력을 사용합니다.
- 실제 차량과 차종별 동작, 장기 운용은 검증 대상입니다. 공모전 제출용 개발 산출물이며 Pleos App Market 입점·양산 승인을 의미하지 않습니다.
- APK는 프로토타입용 debug 서명입니다. 공모전 제출 이후 실제 배포에는 팀 소유의 release 서명과 별도 심사가 필요합니다.

## 구조

```text
app/src/main/java/com/carlog/app/
  MainActivity.kt                 진입점 및 차량 화면 크기 적용
  CarLogController.kt            화면 상태, 비동기 저장, 모드 전환
  domain/Models.kt               인계 모델과 수신·입력·위치 유효성 규칙
  data/CarLogDatabase.kt         영속 저장과 읽음·완료 기록
  platform/PleosDataSource.kt    공개 Vehicle·FusedLocation SDK 연동
  platform/VehicleUxGate.kt      차량 화면 사용 제한 확인
  platform/VoiceService.kt       Pleos·Android 음성 서비스 수명 관리
  ui/CarLogApp.kt                반응형 인계 보드, 작성, 기록, 설정
```

## 공식 자료

- [공모전 개발 환경](https://appdevchallenge.auto-hmg.io/guide/development)
- [Pleos 에뮬레이터 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [프로젝트 생성과 SDK 사용 신청](https://document.pleos.ai/docs/pleos-only/create-my-project)
- [SDK 안내](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/)
- [주행 규제 연동](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [심사와 개발 결과물 안내](https://appdevchallenge.auto-hmg.io/guide/judging-awards)

검증 내역은 `VALIDATION.md`를 확인하세요.
