# CarLog 검증 결과

확인일: 2026년 11월 7일

이 문서는 버전별로 실제로 확인한 것만 적어요. v0.2.0은 빌드와 JVM 테스트까지만 확인했고, 기기·에뮬레이터 검증은 아직 다시 하지 않았어요.

---

## v0.2.0 (이번 빌드)

### 빌드 환경

- Windows, JDK 17(Android Studio JBR), Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.21
- Android SDK Platform 35 / Build Tools 35.0.0
- 한글 경로 문제를 피하려고 `subst` 드라이브(`Q:\`)에서 빌드

### 확인한 결과

| 항목 | 명령 | 결과 |
|---|---|---|
| 디버그 빌드 | `assembleDebug` | 성공 |
| JVM 단위 테스트 | `testDebugUnitTest` | **76개 모두 통과** |
| Lint | `lintDebug` | 성공, 오류 0개 · 경고 9개 |
| 기기 테스트 APK 컴파일 | `assembleDebugAndroidTest` | 성공(실행은 안 함) |
| release 빌드 | `assembleRelease` | 성공, R8 누락 클래스 오류 없음 |

단위 테스트 내역(76개 — 통합 단계 67개 + 리뷰 수정 후 9개 추가):

| 테스트 | 개수 | 내용 |
|---|---|---|
| HandoverRulesTest | 14 | 수신·입력 규칙, 위치 유효성(10분, 시계 오차 60초) |
| SnapshotMergeTest | 14 | 필드별 마지막 유효값 유지, 모드 간 값 분리 |
| RetentionPolicyTest | 13 | 인계 100건·메모 500건 한도, 방금 저장한 카드 보호 |
| SpeechScriptTest | 11 | 읽어주기 문장, 상대 시각 표현 |
| NoteClassifierTest | 9 | 메모 자동 분류, 주차 위치 판별 |
| VoiceTextTest | 6 | 음성 오류 안내 문구, 엔진 전환 판단 |

- 첫 실행에서 `HandoverRulesTest.recentLocationMustBeValidAndFresh` 1개가 실패했어요. 테스트가 위치 유효기간을 2분으로 가정했는데, 공유 계약(`LocationRules`)은 10분 + 시계 오차 60초예요. 테스트를 계약에 맞게 고친 뒤 67개 모두 통과했어요.
- Kotlin 컴파일 오류·경고는 없어요. `PermissionInfo.protectionLevel` 사용 중단 경고는 `protection`으로 바꿔 없앴어요(minSdk 28).
- Lint 경고 9개: 라이브러리 새 버전 안내 7개(GradleDependency), targetSdk 34 유지 1개(OldTargetApi, 에뮬레이터 API에 맞춤), 선택적 `android.car` reflection 1개(PrivateApi, 의도된 사용). baseline이나 규칙 비활성화는 쓰지 않았어요.
- release 빌드: `missing_rules.txt`가 생기지 않았고 기존 `proguard-rules.pro`로 충분했어요. `keystore.properties` 없이 빌드해 **서명되지 않은** APK예요.

### APK 크기

| APK | 크기 | 비고 |
|---|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 43,698,219바이트(약 41.7 MiB) | 축소 없음. 대부분 Pleos SDK와 의존 라이브러리 |
| `app/build/outputs/apk/release/TEAM_CarLog_release.apk` | 14,384,035바이트(약 13.7 MiB) | R8 + 리소스 축소, 미서명 |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 약 2.3 MB | 기기 테스트용 |

- release의 `classes.dex`는 5.6 MB예요. `proguard-rules.pro`가 `ai.pleos.**` 전체를 유지하기 때문이에요.
- 네이티브 라이브러리 `libuacore.so`가 x86_64 4.0 MB, arm64-v8a 3.6 MB를 차지해요. 그래서 ABI 필터(arm64-v8a, x86_64)가 크기에 실제로 영향을 줘요.
- `material-icons-extended`가 release 의존성에 없는 것을 확인했어요.

### 아직 확인하지 않은 것

- **기기·에뮬레이터 실행 전체.** v0.2.0 APK를 어떤 기기나 Pleos 에뮬레이터에도 설치·실행하지 않았어요.
- **기기 테스트 실행.** `CarLogFlowTest`(새 흐름으로 다시 작성)와 `CarLogDatabaseTest`(24개)는 컴파일만 됐고 `connectedDebugAndroidTest`는 실행하지 않았어요. `CarLogFlowTest`는 시연 샘플과 새 설치(또는 오래 쉰 상태)를 가정하므로 실제 에뮬레이터에서 조정이 필요할 수 있어요.
- 운전자 선택 창, 녹음 시트(Pleos/Android 음성 인식), 인계 읽어주기, PleosVehicleFeed의 5초 수신 동작
- R8로 축소한 release APK의 런타임 동작(축소된 SDK 문제는 실행해야 드러나요)
- Pleos 음성 인식에 RECORD_AUDIO가 필요한지 여부(앱은 녹음 전에 항상 요청해요)
- 화면 재생성 자동 테스트는 없어요. 프로세스 종료 후 작성 중 메모 복원은 범위 밖이에요.

재검증 계획은 [ROADMAP.md](ROADMAP.md)의 첫 항목을 확인해 주세요.

---

## v0.1.0 (이전 프로토타입)

> 아래는 v0.1.0 Kotlin 프로토타입의 기록이에요. v0.2.0에서는 다시 실행하지 않았어요.

### 빌드 환경과 결과

- Windows, JDK 17, Gradle 8.10.2, AGP 8.8.2
- Android SDK Platform 35 / Build Tools 35.0.0
- 공식 Pleos Connect 2.1.0 x86_64 시스템 이미지, Android 14(API 34)
- 가상 기기 원본 화면 2560 × 1440, 차량 앱 영역에서 실행
- `assembleDebug`: 성공
- `testDebugUnitTest`: 14개 통과
- `lintDebug`: 성공, 오류 0개
- 기기 테스트: 7개 통과(데이터베이스 6개, 화면 전체 흐름 1개)

### 자동 검증 내용

- **동작 규칙 14개:** 자기 기록 제외, 모든 다음 운전자에 대한 공개 카드 수신, 특정 수신자 구분, 작성자의 자기 메모 완료 방지, 실제 주행 제한 우선, 시연 주행 입력 잠금, 일반 기기의 시연 허용, 실차 연결 시 제한 미확인 상태 차단, 제한 해제 확인 후 입력 허용, 빈 메모·장문·초과 건수 검증, 위치 범위·시각·유효기간 검증, 읽음과 완료 분리.
- **데이터베이스 6개:** 재연결 후 기록 유지, 읽음의 중복 처리와 미완료 유지, 완료자만 완료 취소 가능, 수신 대상의 저장 계층 검증, 시연/실차 모드 데이터 분리, 전체 삭제 시 메모·읽음 기록의 연쇄 삭제.
- **화면 전체 흐름 1개:** 아버지의 메모 표시 → 어머니의 읽음 확인 → 미처리 유지 → 조치 완료 → 지난 기록에서 완료 취소 → 새 인계 위치·메모 입력 → 키보드가 열린 상태에서 저장 버튼까지 스크롤 → 카드 저장 → 아버지 프로필 전환 → 어머니가 작성한 메모 수신.

저장 테스트에서 발견한 키보드 가림 문제는 IME 인셋을 적용해 수정했어요. 비동기 읽음·완료 처리 중 추가 저장 요청이 겹치지 않도록 처리 상태도 화면에 반영했어요.

### 직접 확인한 항목(v0.1.0)

- 공식 Pleos 에뮬레이터에 APK 설치 및 한국어 화면 표시
- 차량 앱 영역에서 화면 크기를 반영한 인계 보드와 작성 화면
- Pleos Vehicle SDK로 배터리 잔량과 누적 주행거리 조회 성공
- 미지원 값은 시연값으로 대체하지 않고 조회 불가로 표시
- 위치 SDK의 권한 미허용 응답 확인, 권한 허용 후 실제 SDK 좌표 수신 성공
- 이 에뮬레이터에서 연료와 워셔액 경고는 조회 불가로 확인

---

## 공통으로 별도 확인이 필요한 항목

- 실제 차량 및 차종별 지원 범위와 권한 승인
- 한국어 음성 인식·읽어주기의 실제 마이크·스피커 품질
- 실제 주행 상태 변경에 따른 플랫폼 차단과 복귀의 실차 검증
- 장기 보관·대량 기록·여러 사람이 반복 사용했을 때의 사용성
- Pleos App Market 배포 심사와 팀 소유 release 서명

이 결과는 공모전 프로토타입 검증 내역이에요. 에뮬레이터의 값은 실차 측정값이 아니며, 시연 데이터와도 구분해요.
