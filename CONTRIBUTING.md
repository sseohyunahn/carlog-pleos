# CarLog 협업 안내

저장소는 공개되어 있습니다. 친구나 외부 참여자는 별도 초대 없이 Fork한 뒤 PR로 수정사항을 제안할 수 있습니다. 원본 저장소에 직접 push하는 권한은 관리자가 GitHub 아이디로 초대한 협업자에게만 부여합니다.

## 앱을 먼저 확인하기

[공모전 제출 APK](https://github.com/halfsamoan/carlog/blob/main/apk/carlog_CarLog_release.apk)를 받아 설치할 수 있습니다. 실행하면 가상 데이터로 동작하는 시연 모드가 열립니다. 공식 검증 환경은 Pleos Connect 2.1.0 에뮬레이터입니다. 일반 Android 기기에서의 실행 가능성을 고려해 구성했지만, 기기별 동작과 음성 품질은 별도 확인이 필요합니다.

## 개발 환경 준비

1. GitHub에서 [Fork](https://github.com/halfsamoan/carlog/fork)합니다.
2. 본인의 Fork를 PC로 내려받습니다. 아래 `YOUR_GITHUB_ID`를 본인 GitHub 아이디로 바꿉니다.

   ```shell
   git clone https://github.com/YOUR_GITHUB_ID/carlog.git
   cd carlog
   git remote add upstream https://github.com/halfsamoan/carlog.git
   ```

3. Android Studio에서 프로젝트 폴더를 열고 JDK 17, Android SDK Platform 35, Build Tools 35.0.0을 설정합니다.
4. Gradle 동기화를 완료합니다. Gradle Wrapper를 사용하므로 Gradle을 별도로 설치할 필요는 없습니다.
5. 차량 SDK 기능을 확인하려면 [공식 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)에 따라 Pleos Connect 2.1.0 에뮬레이터를 설치하고 Cold Boot로 실행합니다.

Windows에서는 프로젝트·SDK·Gradle 캐시에 영문 경로를 사용하는 것을 권장합니다. 서버 인증 정보 없이도 기본 시연 모드를 개발할 수 있습니다. Pleos 데이터와 음성 기능은 실제 실행 환경의 서비스·권한·언어 모델 지원에 따라 달라집니다.

## 작업 순서

1. [Issues](https://github.com/halfsamoan/carlog/issues)에서 작업을 고릅니다. 다른 사람과 작업이 겹치지 않도록 어떤 범위를 진행할지 댓글로 남깁니다.
2. 작업용 브랜치를 만듭니다.

   ```shell
   git switch -c fix/short-description
   ```

3. 변경하고 필요한 검증을 진행합니다.
4. 본인 Fork에 push한 뒤 `halfsamoan/carlog`의 `main`을 대상으로 PR을 만듭니다. PR에는 해결한 문제, 변경된 동작, 확인 방법과 연결 이슈를 적습니다.

## 변경 확인

Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

macOS / Linux:

```shell
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Pleos 에뮬레이터를 연결한 경우 기기 테스트도 실행할 수 있습니다.

```shell
./gradlew connectedDebugAndroidTest
```

문서만 수정했다면 빌드 대신 링크와 설명이 맞는지 확인하면 됩니다. 화면을 바꿨다면 키보드가 열린 상태, 차량 화면 크기, 시연 모드의 주행 중 입력 잠금도 확인합니다. 데이터 흐름을 바꿨다면 읽음·완료 구분, 수신 대상, 시연·Pleos 기록 분리를 함께 확인합니다.

## 프로젝트의 현재 기준

- 시연 데이터와 Pleos 조회값을 섞지 않습니다. 조회할 수 없는 값은 빈 값과 상태 안내로 표시합니다.
- 읽음 확인은 조치 완료를 뜻하지 않습니다.
- 실제 차량 제어 API를 추가하거나 자동 주차 판정을 확정하는 변경은 별도 설계가 필요합니다.
- 개인 SDK 경로, 계정 인증 정보, 서명 키, 빌드 캐시와 SDK 파일을 커밋하지 않습니다.
- 새로운 기능은 [README의 현재 범위](README.md#프로토타입의-범위)와 개선 이슈를 확인한 뒤 작은 단위로 진행합니다.
