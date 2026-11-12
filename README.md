CarLog (카로그)
차량 공유 인수인계 앱 | 현대자동차그룹 차량용 앱 개발 공모전 출품작

CarLog는 한 차량을 여러 사람이 번갈아 사용할 때 주차 위치·차량 상태·전달 메모를 다음 운전자에게 인계하는 Pleos Connect 기반 Android 앱입니다. 다음 운전자는 내용을 확인하고 조치 완료 여부를 기록할 수 있습니다.
시연 영상 및 APK
시연 영상 보기 · [Release APK 다운로드](apk/carlog_CarLog_release.apk)
- APK 경로: apk/carlog_CarLog_release.apk
- 앱 버전: v0.3.2 (com.carlog.app)
주요 기능
- 운전자별 인계 카드와 주차 위치 직접 입력 및 재사용
- 차량 상태 조회 및 인계 시점의 값 저장
- 텍스트·음성 메모와 인계 내용 읽어주기
- 메모 확인과 조치 완료 상태 구분
- 주행 중 입력 제한 및 시연 데이터와 Pleos 데이터 분리
실행 방법
Android 9(API 28) 이상에서 실행할 수 있으며 arm64-v8a와 x86_64를 지원합니다.
1. 위 링크에서 APK를 내려받아 Android 기기 또는 Pleos Connect 에뮬레이터에 설치합니다.
2. 앱을 실행하면 시연 모드에서 주요 기능을 확인할 수 있습니다.
3. 차량 데이터를 사용하려면 설정에서 Pleos 연결을 선택하고 필요한 권한을 허용합니다.
ADB를 사용하는 경우:
adb install -r apk/carlog_CarLog_release.apk
adb shell am start -n com.carlog.app/.MainActivity
소스 코드 빌드에는 JDK 17·Android SDK 35·Gradle Wrapper 8.10.2가 필요합니다.
# Linux
./gradlew assembleDebug

# Windows PowerShell
# .\gradlew.bat assembleDebug
테스트 환경 및 Pleos 화면 규격
항목	내용
Pleos Connect 에뮬레이터	2.1
차량 에뮬레이터	Android 14 (API 34), x86_64
전체 화면 해상도	2560 × 1440 px, 160dpi
휴대폰 테스트 환경	Android 15 (API 35) 에뮬레이터
개발 환경	Kotlin 2.0.21, Jetpack Compose, Android SDK 35
Pleos SDK	Vehicle 2.3.2 · FusedLocation 1.1.3 · SpeechToText 3.3.1 · TextToSpeech 3.3.1


화면 해상도는 에뮬레이터 전체 화면 기준이며 앱의 실제 표시 영역은 창 구성에 따라 달라집니다. Pleos 연동에는 해당 SDK와 서비스 이용 환경 및 권한이 필요합니다.
검증 범위
v0.3.2 검증 기록상 단위 테스트 93개·차량 에뮬레이터 테스트 27개·휴대폰 에뮬레이터 테스트 29개가 통과했습니다. 서명된 Release APK의 설치와 실행도 에뮬레이터에서 확인했습니다.

출 APK는 시연 및 평가용 프로토타입입니다. 그러므로 실제 차량의 센서 연동과 음성 인식 품질은 
