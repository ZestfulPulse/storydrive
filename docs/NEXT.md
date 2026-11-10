# 다음 작업 (2026-11-10 기준)

마감: 2026-11-14. 완료: M0~M4, v0.1.0 릴리스. ANR 수정과 B 단위 테스트는 `fa15e90`, 문서는 `330f48e`.
**v0.1.0 릴리스 완료 (2026-11-10, 사용자 승인).** 태그 `v0.1.0` → `330f48e`, Release: https://github.com/ZestfulPulse/storydrive/releases/tag/v0.1.0
- APK: https://github.com/ZestfulPulse/storydrive/releases/download/v0.1.0/storydrive-v0.1.0.apk (SHA-256 `fbb19ea3…6e4c`)
- 로그아웃 상태 확인: Releases 페이지 200, 제목·SHA-256 표시, APK 다운로드 200(38,910,385바이트), 받은 파일 SHA-256 일치
- Release 설명 원본: `C:/Users/cwone/storydrive-release/release_notes.md`

## 1. 현재 상태: 릴리스 서명과 크래시 수정

### 릴리스 서명
- 키스토어: `C:/Users/cwone/keystore/storydrive-release.jks` (저장소 밖). 값은 `~/.gradle/gradle.properties`의 `STORYDRIVE_RELEASE_*` 4줄에서 읽는다 (`c98cddc`).
- 서명 인증서: `CN=StoryDrive`, SHA-256 `f046086f8abe4223f66c6659e25f39769abbdaf6e727b9c7cb268d8b6ebd6c94` (APK Signature Scheme v2)
- 에뮬레이터의 디버그 키 서명 앱은 삭제했고, 릴리스 키로 서명한 APK로 교체했다.

### 크래시 수정 `f6589d7`
- 증상: 실행 직후 SDK 시작(첫 화면 이후) 전에 화면이 재생성되면 앱이 종료됐다. 에뮬레이터 부팅 직후 첫 실행에서 실제로 발생했다.
- 원인: `onDestroy`에서 `speaker.shutdown()`이 Pleos TTS SDK `stop()`을 불렀는데, `initialize()` 전이라 SDK 내부에서 `UninitializedPropertyAccessException: lateinit property tts has not been initialized`가 났다. 같은 이유로 SDK 시작 전에 "이야기 잠시 멈추기"를 눌러도 종료될 수 있다(코드상 추정, 재현은 못 함).
- 수정: `KoreanSpeaker`는 `start()` 전에 `stop()`·`shutdown()`을 무시한다. `MainActivity`는 이미 종료된 화면에서 지연 SDK 시작을 건너뛴다.
- 재현 방법(수정 전 빌드에서 매번 재현됨): `am start` 후 0.5초 안에 `adb shell settings put --user 10 system font_scale 1.15`. 시험 뒤에는 `settings delete --user 10 system font_scale`로 되돌린다.

### ANR 수정 (2026-11-10, 미커밋)
- 증상: Cold boot 직후 부하가 높을 때(load average 20~26) 앱이 ANR로 종료됐다. exit-info `reason=6 (ANR)`, crash 버퍼 0건.
- 원인: `VehicleSignalMonitor.start()`가 메인 스레드에서 차량 SDK `getCapabilityConfig`를 신호마다 동기 호출했다. 약 12초 걸렸고(`HVAC_SEAT_TEMPERATURE` 하나만 약 7.6초), 그동안 화면이 멈췄다(Skipped 949 frames). 시스템은 15.7초에 ANR로 판정했고 38초에 앱을 종료했다. TTS 초기화 자체는 빨랐지만 `onServiceConnected`가 메인 스레드를 기다리느라 늦었다. 이전 2회차의 "원인 불명 종료"도 같은 원인으로 본다(crash 0건, 부하 약 18. exit-info가 지워져 확정은 못 함).
- 수정:
  - `VehicleSignalMonitor`: 초기화, capability 확인, 구독 등록·해제, release를 전용 `HandlerThread("vehicle-sdk")`에서 한다. `start()`·`stop()`은 작업을 넘기기만 하고 바로 돌아온다. 신호 하나의 예외는 그 신호만 "사용 안 함"으로 처리한다.
  - 확인한 사실: 차량 서비스가 아직 연결되지 않았으면 SDK가 요청을 쌓아 두었다가, 연결 뒤 capability 콜백과 구독 등록을 binder 스레드에서 실행한다. 그래서 구독 등록과 해제를 잠금으로 보호하고, `stop()` 뒤에는 등록하지 않는다.
  - `MainActivity.onMain`: SDK 콜백을 메인 스레드로 넘기고, 화면이 이미 닫혔으면 버린다.
  - 화면: 신호 확인이 끝날 때까지 "차가 들려준 신호" 자리에 "차량 신호 연결 중 (n/8)"을 보인다. 신호별로 확인이 끝나는 대로 숫자가 오른다.
  - 메인 스레드 점검: TTS SDK 호출과 `scenes.json` 읽기에 `[호출 시간]` 로그를 남긴다(50ms 이상은 경고). 1회차 측정 결과: TTS 생성 1~19ms, initialize 69~73ms, addEventListener·setLocale 1~2ms, shutdown 232ms, scenes.json 2ms. TTS 호출은 메인 스레드에 그대로 두었다(SDK가 `onServiceConnected`를 메인 스레드로 보내므로 다른 스레드에서 부르면 SDK 내부 상태와 겹칠 수 있음).
  - 남은 메인 스레드 지연: 부하가 높을 때 첫 화면 그리기 자체에 6~8초가 걸렸다(Skipped 274~515 frames, SDK 시작 전). SDK 호출 때문은 아니다.

### SDK 준비 전 멈추기 단위 테스트 (2026-11-10, 기준 3번 대체)
- `KoreanSpeaker`가 Pleos TTS SDK를 `TtsEngine` 인터페이스(실제: `PleosTtsEngine`)로 받도록 바꿨다. 동작과 로그 내용은 같고, 로그는 주입한 함수로 남긴다(앱에서는 `android.util.Log`, 우선순위도 그대로).
- `PauseBeforeReadyTest` 5개: 가짜 엔진은 `initialize()` 전 SDK 호출에 `UninitializedPropertyAccessException`을 던진다(실제 SDK 동작). SDK 시작 전·TTS 준비 전 멈추기, 그때 화면 종료, 차량 신호가 먼저 온 경우, 준비 뒤 시작·멈춤 정상 재생을 검증한다.
- 검증: `KoreanSpeaker`의 `started` 가드를 빼면 2개가 실패하는 것을 확인했다(되돌림).

### 보관한 릴리스 APK (v0.1.0 업로드용, 2026-11-10 23:18 빌드)
- 경로: `C:/Users/cwone/storydrive-release/storydrive-v0.1.0.apk` (저장소·키스토어 폴더와 분리)
- SHA-256: `fbb19ea30dcd3ec29ca81fd9fad42546ed401c87f9dc148027e0ff5637ef6e4c`
- 서명: APK Signature Scheme v2, `CN=StoryDrive`, 인증서 SHA-256 `f046086f…6c94` (위 릴리스 인증서와 같음)
- 빌드 소스: ANR 수정 + `TtsEngine` 분리. 빌드 시점 소스 지문(앱 소스·빌드 파일 36개 SHA-256)을 저장해 두고, 커밋 뒤 같은지 확인한다.
- 이 APK로 한 확인: 기기 안 SHA-256 일치, 일반 실행 시작·멈춤 정상(시작 → onStart 0.86초, 멈춤 → onStop 0.03초. 첫 시작 탭은 시스템이 홈 화면 입력 영역으로 보내 한 번 더 누름). Cold boot 시험은 바로 앞 APK(`a8ee4724…1084`, `storydrive-v0.1.0_coldboot-tested-a8ee4724.apk`로 보관)로 했다. 두 APK의 차이는 `KoreanSpeaker`의 SDK 호출 분리뿐이다.
- 이전 APK(`f6589d7`, `10d5fca7…42cb`)는 `storydrive-v0.1.0_old-f6589d7.apk`로 보관. 업로드하지 않는다.
- 업로드 전에 SHA-256을 다시 확인한다.
- 시험 로그: `C:/Users/cwone/storydrive-release/test-logs/` (`r1` = 이전 APK, `n1`~`n3` = `a8ee4724` APK). 저장소에는 넣지 않는다.

## 2. 지금까지의 시험 결과 (2026-11-10)
- 단위 테스트: 35개 모두 통과 (`f6589d7`, 강제 재실행). ANR 수정 뒤에도 35개 모두 통과. B 단위 테스트 5개 추가 뒤 40개 모두 통과 (debug·release, `--rerun-tasks`).

### 이전 APK(`f6589d7`) 3차 시험 1회차 (ANR 발견, 이 회차 뒤 중단)
| 시험 | 결과 |
|---|---|
| A | 무효 (`MainActivity created` 1번), 살아 있음 |
| B | 무효 (기기 안 4개 루프로 탭해도 메인 스레드가 막혀 TTS 준비 전에 처리 안 됨). **ANR로 종료** (exit-info `reason=6 (ANR)`, crash 0건) |
| C | 확인 못 함 (버튼 로그 없음), 살아 있음 |

- 부하: 부팅 직후 16, 시험 중 20~26. Skipped frames: B 949, C 484 (둘 다 SDK 시작 뒤).
- 원인이 앱 안에 있어 남은 2회를 쓰지 않고 중단했다. 수정 뒤 새 APK로 시험을 처음부터 다시 한다(아래 "새 APK 시험").

### 이전 APK(`f6589d7`) 2차 시험
- Cold boot 시험 2차 3회 (1차 3회는 Cold boot 뒤 앱이 설치되어 있지 않아 무효):

| 회차 | A: Cold boot 첫 실행 + 0.5초 안 화면 재생성 | B: 실행 직후 "잠시 멈추기" | C: 일반 실행·시작·멈춤 |
|---|---|---|---|
| 1 | 유효, 앱 살아 있음 | 무효 (TTS 준비 뒤에야 눌림), 살아 있음 | 정상 (음성 시작·중지 확인) |
| 2 | 무효 (화면 재생성 안 일어남), 살아 있음 | **B 뒤 프로세스 없음, 원인 불명** | 버튼 로그 없음, 확인 못 함 |
| 3 | 유효, 앱 살아 있음 | 무효, 살아 있음 | 정상 |

- 요약: **A 유효 2회 모두 정상, B 유효 0회, 2회차 원인 불명 종료 1건.**
- 2회차 종료: crash 로그 버퍼에 앱 항목이 0건이었다. exit-info는 다음 회차 재설치로 지워져 원인을 알 수 없다. 당시 에뮬레이터 load average가 약 18로 매우 높았다.
- 확인된 사실: 빠른 부팅(스냅숏) 상태에서 설치한 앱은 Cold boot(`-no-snapshot-load`) 뒤 사라진다. Cold boot한 상태에서 설치한 앱은 남는다. 그래서 Cold boot 뒤에는 매번 설치부터 한다.

### 새 APK(`a8ee4724…1084`) Cold boot 시험 (3회 모두 사용, 2026-11-10 완료)
| 회차 | 부하 | A | B (1·2회차 사용자가 직접, 3회차 자동 탭) | C | Skipped frames / ANR |
|---|---|---|---|---|---|
| 1 | 부팅 직후 16, A 중 21, B 중 15~21 | 무효 (재생성은 SDK 시작 10초 뒤에 일어남), 살아 있음, 재생성 뒤에도 정상 | 무효. 누름 로그 2건이 TTS 준비보다 31초, 차량 확인 완료보다 23초 늦게 찍힘(사용자는 "연결 중" 표시를 보며 누름. 시스템이 멈춰 있어 입력이 늦게 전달된 것으로 봄). 05:30:50 **system_server Watchdog 종료**(android.fg가 GNSS 위치 처리에서 97초 막힘)로 앱 포함 모든 앱 종료. 앱 ANR·crash 0건 | 실행 못 함. system_server가 다시 재시작되고 사용자 10이 내려감 | A: 68, 274, 36, 515, 208, 69, 62 / B: 54, 302, 69, 135, 50 (가장 큰 값은 SDK 시작 전 첫 화면 그리기) / 앱 ANR 0건 (다른 앱 ambientai·systemui 등 ANR 다수) |
| 2 | 부팅 직후 7.6, A 중 13, B 준비 때 26, B·C 중 10~13 | **유효** (created 2회 → SDK 시작), 살아 있음 | 무효. 첫 누름이 TTS 준비보다 7.7초, 차량 확인 완료보다 3.5초 늦음(누름 로그 78건, 사용자는 "연결 중" 표시를 보며 누름). SDK 시작→TTS 준비가 0.68초뿐이라 손으로 맞추기 어려움. 살아 있음, 화면 정상(스크린숏 확인) | 정상 (시작 → onStart 0.46초, 멈춤 → onStop 0.1초) | A: 36, 183 / B: 35, 99 / C: 68 / 앱 ANR 0건, crash 0건, exit-info는 시험 스크립트의 FORCE STOP만 |
| 3 | 부팅 직후 8, A 중 16, B 중 22~30, C 중 23 | 무효 (재생성 안 일어남), 살아 있음 | 무효. **자동 탭**(사용자 선택): `am start -W`가 앱 프로세스 생성 전에 반환돼 처음 탭 일부는 앱 창이 뜨기 전에 다른 화면에 들어감. 앱 창 위 탭은 버튼 로그 0건. 살아 있음 | 확인 못 함. 시작·멈춤 탭 2번 모두 InputDispatcher "No new touched window"(시스템이 앱 창에 입력을 넘기지 못함). 같은 때 systemui ANR. 앱은 살아 있음 | A: 0 / B: 124, 35, 62 / C: **1207, 986** (SDK 시작 전 앱 시작 구간, 앱 CPU 25%, major fault 168, 시스템 CPU 압력 some 54~60%) / 앱 ANR 0건, crash 0건, exit-info는 FORCE STOP만 |

- 요약 (새 APK):
  - A 유효 1회(2회차), 종료 0건. 이전 APK의 유효 2회와 합쳐 3회, 종료 0건.
  - B 유효 0회. 1·2회차 수동 누름, 3회차 자동 탭 모두 TTS 준비 뒤에 처리되거나 처리되지 않음. 메인 스레드가 막히지 않게 된 뒤 SDK 시작→TTS 준비 구간이 0.2~0.7초로 짧아짐(부하가 높으면 수 초).
  - 앱 종료 1건(1회차)은 system_server Watchdog 종료 때문(앱 밖). 앱 ANR·crash 0건.
  - C 정상 1회(2회차). 1회차는 시스템 재시작으로 실행 못 함, 3회차는 시스템이 입력을 앱 창에 전달하지 못함.
  - 남은 메인 스레드 지연: SDK 시작 전 앱 시작 구간(첫 화면 그리기 등)이 부하가 높을 때 최대 20초(Skipped 1207). ANR로 이어지지는 않았지만, 이 사이에 사용자가 화면을 누르면 입력 ANR이 날 수 있다.
- 릴리스 기준 판정(처음): 3번(B 유효 1회 이상)과 5번(매 회차 C 정상)을 채우지 못함. 2·4·6번은 충족. → 아래 4장의 2026-11-10 사용자 결정으로 기준을 조정했다.
- 로그: `C:/Users/cwone/storydrive-release/test-logs/n1`~`n3` (회차별 exit-info, crash, 단계별 logcat), 이전 APK 1회차는 `r1`.

## 3. 다음 시험 조건 (9시 재개)
- Cold boot는 **최대 3회**. 3회를 넘기지 않는다.
- 조건이 맞지 않은 시험(무효)은 **무효로 기록만 하고 재시도하지 않는다.**
  - A 유효 판정: 로그에서 `MainActivity created`가 `첫 화면 이후 SDK 시작`보다 먼저 두 번 나온다.
  - B 유효 판정: `[버튼] 이야기 잠시 멈추기`가 `첫 화면 이후 SDK 시작`보다 먼저, 또는 `TTS 초기화 성공` 전에 나온다.
- 회차마다 순서:
  1. Cold boot
  2. `adb install -r` 후 기기 안 APK SHA-256 확인
  3. A, B, C
- 회차마다 **다음 회차 재설치 전에** 저장한다:
  - `adb shell dumpsys activity exit-info com.zestfulpulse.storydrive`
  - `adb logcat -b crash -d`
  - 앱 로그 (`adb logcat -d`)
  - 파일 이름에 회차 번호를 붙인다.
- B는 adb 탭으로 시간 창을 맞추기 어렵다. 필요하면 사용자가 화면에서 직접 누르고, Claude는 로그만 확인한다.
- 시험 중 에뮬레이터 부하가 높으면(load average 10 이상) 기록해 둔다.

## 4. 릴리스 판단 기준 (2026-11-10 사용자 확정)
아래를 모두 만족하면 v0.1.0 태그·Releases를 만들기 전에 사용자에게 결과를 보여주고 승인을 받는다.
1. 단위 테스트 모두 통과 (35개, B 테스트 추가 뒤 40개)
2. A: 유효 시험에서 앱 종료 0건 (이번 2회 + 다음 시험 유효분 합산 3회 이상)
3. B: 유효 시험 1회 이상, 종료 0건 (사용자가 직접 시험해도 됨)
4. 원인 불명 종료: 다음 시험에서 다시 생기지 않거나, exit-info·crash 로그로 원인이 앱 밖(시스템 부하 등)임을 확인
5. C: 매 회차 음성 시작·중지 정상
6. 업로드할 APK의 SHA-256이 시험한 APK와 같고, 서명 인증서가 위의 릴리스 인증서와 같음 — 업로드 APK는 Cold boot 시험 APK(a8ee4724)와 KoreanSpeaker의 SDK 호출 분리만 다르며, 일반 실행과 단위 테스트 40개로 확인함 (2026-11-10 사용자 승인)

### 2026-11-10 사용자 결정 (기준 조정)
- 3번 B: 에뮬레이터에서 SDK 시작→TTS 준비 구간이 0.2~0.7초로 짧아 손·자동 탭으로 맞출 수 없었다. **"TTS·차량 SDK 준비 전에 이야기 잠시 멈추기를 눌러도 안전하다"를 단위 테스트(`PauseBeforeReadyTest`)로 검증해 3번을 대신한다.**
- 5번 C: **시스템이 입력을 앱에 전달하지 못한 회차(InputDispatcher "No new touched window", systemui ANR, system_server 재시작)는 무효로 본다.** 유효한 2회차 결과와 M4 화면 시험(사용자 직접 확인)으로 5번을 충족으로 판정한다.
- 시작 구간 지연(부하가 높을 때 SDK 시작 전 최대 20초)은 이번 릴리스에서 고치지 않는다. Release 설명과 README 알려진 제약에 "Pleos 에뮬레이터 권장 사양(외장 그래픽) 미만 PC에서는 첫 화면 표시와 터치 반응이 늦어질 수 있음"을 넣는다.
- 시험 로그는 `C:/Users/cwone/storydrive-release/test-logs/`에 보관하고 저장소에는 넣지 않는다.
- 단위 테스트 추가로 앱 코드가 바뀌면 APK를 다시 빌드하고 해시를 새로 기록한다. 커밋 전 작업 트리가 APK 빌드 소스와 같은지 확인하고, v0.1.0 태그와 APK가 같은 코드를 가리키게 한다.

릴리스 내용(사용자 지시): 태그 `v0.1.0`, 제목 "스토리드라이브 시제품 v0.1.0", 파일 `storydrive-v0.1.0.apk`, 한국어 설명(한 줄 소개, 포함 기능, 설치 방법, README 시연 방법 링크, 알려진 제약, APK SHA-256). 올린 뒤 로그아웃 상태에서 Releases 페이지와 APK 다운로드 확인, README 진행 상황 줄을 "APK: Releases v0.1.0"으로 바꿔 커밋·푸시.

## 5. 실행 시간 측정 (M4 때 측정, 기록용)
| 항목 | 결과 |
|---|---|
| 앱 실행, 릴리스 빌드, 강제 종료 후 3회 | 2.6 / 1.7 / 1.6초 (기준 10초 통과) |
| 앱 실행, 디버그 빌드 (참고) | 10.3~12.2초. 제출은 릴리스 기준 |
| "이야기 시작" 탭 → 음성 시작 | 0.28~0.65초 (기준 2초 통과) |
| "이야기 잠시 멈추기" 탭 → 음성 중지 | 0.28~0.38초 |

- 2026-11-10 릴리스 키 빌드: 실행 2.0~3.0초 (Cold boot 직후 포함)
- 실행 직후 TTS 준비까지 3.5~8초 걸린다. M5에서 음성 준비 전에는 "이야기 시작"을 비활성화한다.

## 6. 남은 작업
- 녹화 전 디버그 표시(앱 이름 길게 누르기)가 꺼져 있는지 확인
- 타이머 침묵 재검토: 늦은 onDone으로 문장 뒤 4~6초 침묵이 생길 수 있다. 배율(글자 수 × 0.25초 × 1.5 + 2초)은 사용자 결정으로 유지 중, 60초 녹화에서 거슬리면 다시 검토
- M5 안전 동작
  - 문 열림이 문장 재생 도중 끊는 것을 에뮬레이터에서 확인 (단위 테스트로는 검증됨)
  - 주행 중(기어 D)과 음성 준비 전에는 "이야기 시작" 비활성
  - 벨트 사건은 콘솔에서 바꿀 수 없음 → 단위 테스트로 검증 (이미 있음)
  - "이야기 잠시 멈추기" 중에 문이 열렸다 닫힌 경우 상태 확인
- M6: 깨끗한 환경에서 저장소를 받아 빌드되는지 확인, 저장소 전체 키·개인정보 검색

## 참고: 작업 환경 메모
- 명령줄 빌드는 `JAVA_HOME = C:\Program Files\Android\Android Studio\jbr` 필요. Git Bash에서 `MSYS_NO_PATHCONV=1`을 켠 채로 gradlew를 실행하면 JAVA_HOME 경로가 바뀌지 않아 빌드가 실행되지 않는다(adb 명령에만 켤 것).
- 빌드 중에는 에뮬레이터 부하가 높아 `adb install`이 Broken pipe로 실패할 수 있다. 잠시 뒤 다시 시도한다.
- 에뮬레이터 화면 캡처는 `adb emu screenrecord screenshot <폴더>` (screencap은 검은 화면, 부팅 직후 첫 캡처도 검을 수 있음)
- 버추얼 콘솔: `adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088/
  - 문 열기는 Emulation Controls > Door Position (메인 화면 BODY 문 버튼은 잠금만 바뀜)
  - 열선은 Emulation Controls > Seat Temperature > Driver
- 공식 샘플은 저장소 밖 `~/projects/pleos-sample-vehicle-app`에 참고용으로만 둔다
