# v0.3.2 — 2026-11-12

- 작은 휴대폰 창에서 차량용 헤더·메뉴·하단 문구가 본문을 가리던 문제 수정.
- 작은 창 전용 배치와 스크롤 본문, 큰 글씨·회전·키보드 입력 대응. 차량 화면과 두 테마의 색상은 유지.
- SDK manifest 병합으로 자동차 기능이 필수로 바뀌던 문제 수정. 일반 휴대폰에서도 실행 가능.
- 동일한 로컬 release 서명키로 versionCode 5 / versionName 0.3.2 생성.
- 단위 테스트 93개, 차량 에뮬레이터 27개, 휴대폰 에뮬레이터 29개 통과. 서명된 release APK의 입력·저장·재실행·재설치 보존 확인.
- 상세 결과와 검증 한계는 [VALIDATION-v0.3.2.md](VALIDATION-v0.3.2.md) 참고.

# v0.3.1 — 2026-11-12

- 첫 번째 디자인·음성 ZIP의 기능과 디자인을 기준으로 Android 화면 구현.
- 두 번째 공유 ZIP의 파랑·네이비 팔레트 적용, Asta Sans 포함, 차량 화면 렌더링 및 개인 서명 확인.

# v0.3.0 — 2026-11-08

- 블루·화이트 주간 화면과 네이비 야간 화면, 선택 저장.
- 큰 글자와 주요 조작 영역, 주차 위치/현재 상태/과거 상태 구분.
- 중요·미확인 필터, 기록 검색과 처리 상태 필터.
- 두 열 작성 화면, 고정 저장 버튼, 메모 수와 전달 대상 요약.
- 기존 내용을 보존하는 빠른 문구, 미저장 상태의 운전자 변경 확인.
- 새로운 검색/정렬 규칙 12개 단위 테스트 및 에뮬레이터 UI 검증.

# 변경 내역

## v0.2.0 — CarLog v2 (2026-11-07)

Kotlin/Jetpack Compose 프로토타입(v0.1.0)을 바탕으로 이전 Java 앱(v1)의 장점을 합친 버전이에요. versionCode 2, versionName `0.2.0`.

검증 상태: 디버그 빌드, JVM 단위 테스트 76개, Lint(오류 0), release R8 빌드까지 통과했어요. 기기·Pleos 에뮬레이터 실행과 기기 테스트는 아직 다시 하지 않았어요([VALIDATION.md](VALIDATION.md)).

### Kotlin 프로토타입(v0.1.0)에서 이어받은 것

- Jetpack Compose 차량 화면 구조와 인계 보드·작성·기록·설정 탭
- 공유 프로필과 수신 대상(모두/특정 프로필) 지정, 자기 기록 제외 규칙
- 읽음 확인과 조치 완료 분리, 완료한 사람의 되돌리기
- 시연/Pleos 모드별 데이터베이스 분리, 시연값이 실차 데이터에 섞이지 않는 규칙
- CarUxRestrictions 기반 입력 잠금(선택적 `android.car` 연동), 시연용 주행 잠금·GPS 음영
- 위치 유효성 규칙(`LocationRules`), 입력 검증(카드당 메모 10개 등)
- SQLite 트랜잭션·외래키 저장, Android 백업·기기 이전 제외

### v1(Java 앱)에서 가져온 것

- **운전자 선택 창:** "이번 운전자는 누구인가요?"를 첫 실행과 20분 넘게 비운 뒤 표시, "그대로 ○○" 유지
- **받은 인계 카드:** 작성자·시각, 주차 위치, 에너지·주행 가능 거리, 문·창문·워셔액 경고, 확인할 메모 수
- **10초 음성 메모:** 실시간 자막·음량·남은 시간, 주차 위치 음성 입력
- **메모 자동 분류**(`NoteClassifier`, v1 키워드 규칙을 v2 분류에 맞춤)
- **인계 읽어주기**(`SpeechScript`): 작성자·시각·주차·차량 경고·메모 순서, 주행 잠금 화면에서도 사용
- **음성 엔진 자동 전환:** Pleos ON_DEVICE → Android 음성 인식/TTS → 직접 입력
- **마지막 유효값 유지**(`SnapshotMerge`, v1 `LastValidSnapshot` 이식)와 필드별 수신 시각
- **라운드 방식 차량 수신**(`PleosVehicleFeed`): 연결 유지, 5초 주기, 10초 넘은 라운드 폐기
- **차량 항목 확대:** 트립 주행거리, 주행 가능 거리, 문 잠금, 창문(CAR_DOORS·CAR_WINDOWS 권한 추가)
- **내가 남긴 메모** 구역과 작성자의 메모 삭제

### v2에서 새로 만든 것

- `CarLogController`를 `AndroidViewModel`로 바꿔 화면 재생성 후에도 모드·운전자·탭·작성 중 메모·녹음 시트 유지
- **지난 주차 위치 사용:** 누가 언제 기록했는지 함께 저장·표시(스키마에 `parking_by`, `parking_at` 추가)
- 음성 메모 표시(`from_voice`)와 "음성으로 받아 적었어요" 안내
- 저장 전 값이 2분 넘게 오래됐으면 다시 읽고 최대 12초 대기
- 설정 화면에 필드별 상태·수신 시각과 사용 중인 음성 엔진 표시
- 데이터베이스 스키마 2: 기존 파일을 그대로 업그레이드(1 → 2), 손상되거나 오래된 스냅샷 JSON도 안전하게 읽기
- release 빌드 설정: `keystore.properties`가 있을 때만 서명, `-PcarlogTeam=팀명`으로 `<팀명>_CarLog_release.apk` 생성

### 성능과 크기

- **N+1 조회 제거:** 기록 목록을 인계·메모·읽음 3개 쿼리로 읽고 메모리에서 합쳐요. `handovers(created_at, id)` 인덱스 추가
- **보관 한도:** 인계 100건, 메모 500건(완료된 오래된 메모부터 정리). 방금 저장한 카드는 항상 보호
- **R8 + 리소스 축소:** release APK 약 13.7 MiB(축소 없는 debug는 약 41.9 MiB)
- **ABI 필터:** arm64-v8a, x86_64만 포함. 네이티브 라이브러리 `libuacore.so`가 ABI마다 3.6~4.0 MB라 효과가 있어요
- **아이콘:** `material-icons-extended`를 빼고 `material-icons-core`만 사용(분류 배지는 글자로 표시)
- **연속 차량 수신:** 한 번씩 읽던 방식을 5초 주기 연속 수신으로 바꾸고, 늦게 온 응답은 토큰으로 무시
- **주행 잠금 확인:** 메인 스레드 밖에서 실행하고 reflection 결과를 캐시, 실패 후 5초 뒤 재시도
- **자원 정리:** 앱 중지 때 차량 수신·음성·주행 잠금 확인을 멈추고, DB는 진행 중인 작업이 끝난 뒤 닫아요

### 삭제·교체

- `platform/PleosDataSource.kt` 삭제 → `platform/VehicleFeeds.kt`(DemoVehicleFeed, PleosVehicleFeed)
- `platform/VoiceService.kt` 삭제 → `platform/voice/` 패키지
- `ui/CarLogApp.kt`를 `BoardScreen.kt`, `EditorScreen.kt`, `HistorySettingsScreens.kt`, `Common.kt`로 분리

### 알려진 한계

- 기기·Pleos 에뮬레이터 실행, `connectedDebugAndroidTest`, R8 축소 APK의 런타임 동작은 미검증
- 프로세스 종료 후 작성 중 메모는 복원되지 않아요(v0.1.0의 `rememberSaveable` 대비 후퇴)
- Pleos 음성 인식의 RECORD_AUDIO 필요 여부 미확인(앱은 항상 요청)
- 기본 빌드 결과는 미서명·팀명 `TEAM`이에요. 제출 전 `keystore.properties`와 `-PcarlogTeam`이 필요해요

## v0.1.0 — Kotlin 프로토타입

- 차량 공유 인수인계 흐름의 첫 Kotlin/Compose 구현. 공식 Pleos Connect 2.1.0 에뮬레이터에서 단위 테스트 14개, 기기 테스트 7개 통과 기록이 있어요([VALIDATION.md](VALIDATION.md)의 v0.1.0 항목).
