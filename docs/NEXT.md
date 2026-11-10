# 다음 작업 (2026-11-10 기준)

마감: 2026-11-14. 완료: M0~M4 (푸시됨, `origin/main` = `f6589d7`).
**v0.1.0 태그와 Releases는 아직 만들지 않았다.** 아래 시험을 마치고 사용자가 승인한 뒤에 만든다.

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

### 보관한 릴리스 APK (`f6589d7`에서 빌드)
- 경로: `C:/Users/cwone/storydrive-release/storydrive-v0.1.0.apk` (저장소·키스토어 폴더와 분리)
- SHA-256: `10d5fca70c9685215b5ee63958759ba44b402bfa2c2d9de4ce27120c364742cb`
- 업로드 전에 SHA-256을 다시 확인한다. 다르거나 파일이 없으면 `f6589d7`에서 다시 빌드하고, 새 해시를 기록한 뒤 그 APK로 시험부터 다시 한다.

## 2. 지금까지의 시험 결과 (2026-11-10)
- 단위 테스트: 35개 모두 통과 (`f6589d7`, 강제 재실행).
- Cold boot 시험 2차 3회 (1차 3회는 Cold boot 뒤 앱이 설치되어 있지 않아 무효):

| 회차 | A: Cold boot 첫 실행 + 0.5초 안 화면 재생성 | B: 실행 직후 "잠시 멈추기" | C: 일반 실행·시작·멈춤 |
|---|---|---|---|
| 1 | 유효, 앱 살아 있음 | 무효 (TTS 준비 뒤에야 눌림), 살아 있음 | 정상 (음성 시작·중지 확인) |
| 2 | 무효 (화면 재생성 안 일어남), 살아 있음 | **B 뒤 프로세스 없음, 원인 불명** | 버튼 로그 없음, 확인 못 함 |
| 3 | 유효, 앱 살아 있음 | 무효, 살아 있음 | 정상 |

- 요약: **A 유효 2회 모두 정상, B 유효 0회, 2회차 원인 불명 종료 1건.**
- 2회차 종료: crash 로그 버퍼에 앱 항목이 0건이었다. exit-info는 다음 회차 재설치로 지워져 원인을 알 수 없다. 당시 에뮬레이터 load average가 약 18로 매우 높았다.
- 확인된 사실: 빠른 부팅(스냅숏) 상태에서 설치한 앱은 Cold boot(`-no-snapshot-load`) 뒤 사라진다. Cold boot한 상태에서 설치한 앱은 남는다. 그래서 Cold boot 뒤에는 매번 설치부터 한다.

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
1. 단위 테스트 35개 모두 통과
2. A: 유효 시험에서 앱 종료 0건 (이번 2회 + 다음 시험 유효분 합산 3회 이상)
3. B: 유효 시험 1회 이상, 종료 0건 (사용자가 직접 시험해도 됨)
4. 원인 불명 종료: 다음 시험에서 다시 생기지 않거나, exit-info·crash 로그로 원인이 앱 밖(시스템 부하 등)임을 확인
5. C: 매 회차 음성 시작·중지 정상
6. 업로드할 APK의 SHA-256이 시험한 APK와 같고, 서명 인증서가 위의 릴리스 인증서와 같음

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
