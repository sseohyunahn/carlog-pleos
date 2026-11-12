# CarLog 디자인 시스템 Compose 적용 안내

기준: `design/tokens/carlog-tokens.json` (v1, 2026-11-08). 이 폴더의 `.kt` 파일은 그 값을 옮긴 것이다.
`carlog-v2` 복사본에 넣어 `compileDebugKotlin` 통과까지 확인했다(2026-11-08, AGP 8.8.2 / Kotlin 2.0.21 / Compose BOM 2024.09.03).

## 파일

| 파일 | 내용 |
|---|---|
| `Color.kt` | 팔레트 `CarLogPalette`, 의미 토큰 `CarLogColors`, `NightColors`/`DayColors`, Material3 색 매핑(primary = 야간 Sand / 주간 Charcoal, 파랑은 Material 역할에 안 씀) |
| `Type.kt` | `CarLogTypography`(feature, headlineL ~ labelS, 버튼 글자), Material3 Typography 매핑, `AstaSans` |
| `Shape.kt` | 모서리 반경 `CarLogRadius`, `CarLogShapes`, Material3 Shapes |
| `Dimens.kt` | 간격, 거터, 버튼 높이(L100/M80/S76), touchMin 76, 아이콘, 창 크기 |
| `CarLogTheme.kt` | `CarLogTheme { }`, `CarLog.colors / type / dimens` |
| `Components.kt` | `CarLogButton`, `HandoverTag`, `LinkDot`, `CategoryBadge` |

## 적용 순서

1. **파일 복사**: 이 폴더의 `.kt` 6개를 `app/src/main/java/com/carlog/app/ui/theme/`에 넣는다(패키지 `com.carlog.app.ui.theme`). 추가 의존성 없음.
2. **글꼴 추가**: Google Fonts에서 Asta Sans(OFL) 정적 TTF를 받아 `app/src/main/res/font/`에 소문자 이름으로 넣는다
   (`asta_sans_regular.ttf`, `asta_sans_medium.ttf`, `asta_sans_semibold.ttf`, `asta_sans_bold.ttf`, `asta_sans_extrabold.ttf`).
   그다음 `Type.kt`의 `val AstaSans = FontFamily.Default` 한 줄을 TODO 주석에 적힌 `FontFamily(Font(R.font.…))`로 바꾼다.
   OFL 라이선스 문구를 오픈소스 고지에 추가한다. 글꼴 없이도 빌드는 된다(시스템 글꼴로 보임).
3. **테마 교체**: `CarLogApp.kt`의 `darkColorScheme(...)` + `MaterialTheme(colorScheme = scheme, typography = Typography())`를 지우고
   `CarLogTheme { ... }`로 감싼다. `Surface(color = Bg)`는 `CarLog.colors.bg`로.
   주간 테마는 선택 사항이다. 에뮬레이터에서 주간 화면을 확인한 뒤에만 `CarLogTheme(darkTheme = isSystemInDarkTheme())`로 바꾼다(차량 주야간 신호를 따름).
4. **색 상수 교체**: `Common.kt`의 색 상수를 아래 표대로 바꾼 뒤 상수를 지운다. 화면 코드에 직접 쓴 `Color(0x…)`도 같이 바꾼다.
5. **크기 교체**: 아래 "바꿀 크기" 참고. 화면별로 `components.css`와 `design/png/*` 시안을 같이 본다.
6. **1.35배 밀도 보정 제거**: 맨 마지막에, Pleos 에뮬레이터에서 확인한 뒤에 한다(아래 참고).
7. 빌드 후 에뮬레이터에서 절반 창(1060dp 폭)과 전체 창(2120dp 폭)을 모두 확인한다.

## 색 매핑 (Common.kt → 새 토큰)

| 기존 | 값 | 새 토큰 | 비고 |
|---|---|---|---|
| `Bg` | #101719 | `CarLog.colors.bg` | |
| `Panel` | #1B2528 | `colors.surface` | 패널, 메모 행, 목록 행 |
| `PanelLight` | #232F33 | `colors.raised` | 배지 바탕, 선택 탭, 안내 박스 |
| `Lime` (버튼 바탕) | #BCF57A | `colors.primary` / 글자 `colors.onPrimary` | 야간 Sand. 주 버튼은 `CarLogButton(style = Primary)` |
| `Lime` (작은 강조 글자·아이콘) | | `colors.accent` | 넓은 면적 금지 |
| `Lime` (선택 막대·연결 점·진행 막대) | | `colors.accentMark` | 글자 없는 표시 전용 |
| `Lime` (단계 번호 `Label`, 로고) | | `colors.textSecondary` / `colors.text` | |
| `Muted` | #9BAEAF | `colors.textTertiary` | 설명 글은 `textSecondary`가 맞는 곳도 있음 |
| `White` | #F2F5EE | `colors.text` | |
| `Amber` | #FFC77A | `colors.warning` | 인계 태그 위에서는 `colors.onTagWarning` |
| `Border` | #344145 | `colors.outline` (입력칸·버튼 테두리) / `colors.divider` (장식 구분선) | |
| `ParkingGreen` | #263A31 | `colors.tag` / 글자 `colors.onTag` | 받은 인계 카드는 `HandoverTag`로 교체 |
| `Color(0xFFBBCCC1)` (BoardScreen) | | `colors.onTagSecondary` | 태그 안 보조 글자 |
| `Color(0xFF47604F)` (태그 구분선) | | `HandoverTag`의 `foot` 슬롯(점선 자동) | |
| `Color(0xFF3B3227)`, `Color(0xFF665339)` (중요 메모) | | 바탕 `colors.raised` + 글리프 `colors.warning`, 왼쪽 6dp `colors.warning` 막대 | `CategoryBadge(important = true)` |
| `Color(0xFF304535)` (secondaryContainer) | | `colors.secondaryButton` | Material 매핑에 이미 들어 있음 |
| `Color(0xFF141E21)` (Rail 바탕) | | `colors.bg` (레일은 바탕과 같은 색, 선택 항목만 `raised` + 왼쪽 `accentMark` 4dp) | |
| 주행 중 잠금(`DrivingOverlay`) | `Bg.copy(.97f)` | `colors.overlayBg` / `overlayText` / `overlayTextSecondary` | 테마와 무관한 고정색. 버튼은 `CarLogStaticColors.OverlayButton` |
| `Pill(…)` / `LinkChip` | | `LinkDot(label, state)` | Connected / Warning / Demo |

## 바꿀 크기

| 항목 | 지금 | 새 값 |
|---|---|---|
| 버튼 높이 | 56~72dp (`height(64.dp)`, `height(72.dp)` 등) | 주 버튼 L 100, 보통 M 80, 행 안 S 76 (`CarLogButton(size = …)`) |
| 터치 대상 최소 | 32~44dp (`IconButton size 32`, 배지 44) | 76dp (`dimens.touchMin`), 대상 사이 24dp (`dimens.targetGap`) |
| 최소 글자 | 11~13sp (하단 문구, 설명, Pill) | 운전자가 읽는 글자 24sp(`type.labelM`). 20sp(`labelS`)는 버전 표시처럼 운전과 무관한 것만 |
| 본문 | 14~19sp | `bodyM` 28 / `bodyL` 30 / `bodyS` 26 |
| 화면 제목 | 24~28sp | `titleL` 40, 섹션 `titleS` 32 |
| 핵심 값(주차 위치 등) | 25~28sp | `feature` 88 (화면당 하나) |
| 모서리 | 12~22dp | 태그 36, 패널 28, 행 24, 시트·대화상자 40 |
| 화면 좌우 여백 | 16 / 28dp | 절반 창 40, 전체 창 56 (`gutterHalf` / `gutterFull`) |
| 레일 폭 | 200dp | 320dp, 항목 높이 88 |
| 넓은 화면 기준 | `maxWidth >= 900.dp` | 새 크기 기준으로 다시 정한다(`dimens.fullLayoutBreakpoint` 1600dp 제안). 1060dp 절반 창은 탭, 2120dp 전체 창은 레일 |

새 값은 이미 Pleos 타입 스케일(dp 단위) 그대로라 지금보다 2배 가까이 크다. 한 화면에 들어가는 양이 줄어드니 화면별 시안(`design/png`)대로 정보를 덜어낸다.

## 1.35배 밀도 보정 제거

`MainActivity.kt`는 차량(`FEATURE_AUTOMOTIVE`)에서 `LocalDensity`를 1.35배로 키운다. 지금 화면이 폰 크기 숫자(14sp, 64dp)라서 넣은 보정이다.
새 토큰은 Pleos 기준 dp라서 보정을 그대로 두면 모든 것이 1.35배 더 커진다(버튼 100dp → 135dp, 절반 창에 안 들어감).

- 크기 교체(5단계)를 끝낸 뒤에 제거한다. 먼저 제거하면 기존 화면이 너무 작아진다.
- 제거 전 Pleos 에뮬레이터에서 `adb shell wm size`, `adb shell wm density`로 실제 값을 확인한다. 토큰은 2560x1440 @160dpi, 앱 영역 1060x1256(절반)을 가정했다(공식 수치 미공개).
- 제거 후 에뮬레이터에서 버튼이 76dp 이상, 메모 글자가 24sp 이상으로 보이는지, 절반 창에 보드 화면이 시안처럼 들어가는지 본다. 크게 다르면 보정을 지우지 말고 density 값을 팀에 공유한다.

## 지켜야 할 것

- 주행 중 자동 애니메이션 금지. `CarLogButton`은 누를 때만 0.95배 + 바탕 8% 어둡게, 애니메이션 없이 바뀐다. 리플도 끔.
- 파랑(`accentMark`)은 점·막대 전용, 의미는 옆 글자가 전달한다. 파랑을 버튼 바탕이나 넓은 면적에 쓰지 않는다.
- 한 화면에서 고대비 덩어리는 `HandoverTag` 하나.
- `Components.kt`는 참고 구현이다. 아이콘은 `leadingIcon`/`content` 슬롯에 넣는다(material-icons-core 사용 가능).
