# SDK 메모

에뮬레이터: Pleos Connect v2.1.0 (Intel x86_64, API 34, Cold boot, RAM 4GB)

## Pleos Vehicle SDK (차량 신호 읽기, M2)

### 사용 버전과 초기화
- `ai.pleos.playground:Vehicle:2.3.2`. 공식 샘플은 2.3.1이지만 저장소에는 2.0.3과 2.3.2만 있다.
- `Vehicle(applicationContext)` → `initialize()`는 동기 호출이고 바로 사용할 수 있다(공식 샘플과 같은 방식). 종료 시 각 `unregister…` → `release()`.
- 구현: `VehicleSignalMonitor.kt`. 신호마다 `check…Capabilities`로 지원 여부(AccessType READ/READ_WRITE)를 확인한 뒤, 지원되는 것만 `register…`로 구독한다. 지원되지 않으면 화면에 "이 차량에서는 사용 안 함"으로 표시한다.
- 모든 Listener에는 `onFailed(e: Exception)`가 있다(구현 필수).

### 신호별 API (클래스·함수 이름은 SDK AAR에서 확인함)
| 신호 | API 객체 | capability 확인 | 구독 (Listener 콜백) | 권한 |
|---|---|---|---|---|
| 브레이크 페달 | `vehicle.getBrake()` | `checkBrakePedalStatusCapabilities` | `registerBrakePedalStatus` (`onBrakePedalStatusUpdated(Boolean)`) | `CAR_DRIVE` |
| 기어 P/R/N/D | `vehicle.getGear()` | 없음 | `registerGearSelection` (`onSelected(VehicleGear)`), **deprecated** | `CAR_DRIVE` |
| 방향지시등 | `vehicle.getTurnSignal()` | `checkTurnSignalStateCapabilities` | `registerTurnSignalState` (`onTurnSignalStateUpdated(TurnSignalValue: OFF/LEFT/RIGHT)`) | `CAR_LIGHTS` |
| 와이퍼 | `vehicle.getWiper()` | `checkWiperStateCapabilities` → `Map<WindowArea, List<WiperState>>` | `registerWiperState(WindowArea, …)` (`onWiperStateUpdated(WiperState: OFF/ON/OTHER/SERVICE)`) | `CAR_WINDOWS` |
| 시트 열선 (모든 좌석 구독, 뒷좌석 우선) | `vehicle.getSeat()` | `checkSeatTemperatureCapabilities` → `Map<SeatArea, AreaBounds<Int>>` | `registerHvacSeatTemperature(SeatArea, …)` (`onHvacSeatTemperatureUpdated(Int)`) | `CAR_SEATS` |
| 도어 | `vehicle.getDoor()` | `checkDoorPositionCapabilities` → `Map<DoorArea, AreaBounds<Int>>` | `registerDoorPosition(DoorArea, …)` (`onPositionUpdated(Int)`) | `CAR_DOORS` |
| 안전벨트 체결 | `vehicle.getSeat()` | `checkSeatBeltBuckledCapabilities` → `List<SeatArea>` | `registerSeatBeltBuckledState(SeatArea, …)` (`onSeatBeltBuckledStateUpdated(Boolean)`) | `CAR_SEATS` |
| 안전벨트 경고 | `vehicle.getSafety()` | `checkSeatNotBuckledWarningCapabilities` | `registerSeatBeltNotBuckledWarning(SeatArea, …)` | `CAR_SAFETY` |

- 권한은 SDK의 `@RequiresPermission` 표기 기준이다(`pleos.car.permission.*`, protectionLevel=normal). 와이퍼에는 `READ_WINDSHIELD_WIPERS`가 아니라 `CAR_WINDOWS`가 필요하다.
- 영역 값: `SeatValue`(ROW_1_LEFT … ROW_3_RIGHT), `DoorValue`(ROW_1_LEFT … ROW_3_RIGHT, HOOD, REAR), `WindowValue`(FRONT_WINDSHIELD, REAR_WINDSHIELD …)

### 에뮬레이터에서 확인한 지원 상태 (2026-11-08)
- 지원: 브레이크(READ), 방향지시등(READ), 와이퍼(READ, 앞·뒤), 시트 열선(READ_WRITE, 1·2열 좌우), 도어(READ_WRITE, 1·2열 좌우·후드·트렁크), 안전벨트 체결(READ_WRITE, 1열 좌우, 2열 좌·중·우)
- 미지원: 안전벨트 경고(`ERROR_NOT_SUPPORTED_PROPERTY`). 그래서 `BELT_WARN`은 안전벨트 체결 상태(체결 → 풀림)로 판단한다. 콘솔에서 벨트를 바꿀 수 없으므로 사건 판단은 M3 단위 테스트로 검증한다. `PRIVATE_CAR_SAFETY`(Telltales)는 쓰지 않는다.
- 기어 API는 deprecated("use the Google GEAR_SELECTION property")이지만 에뮬레이터에서는 동작한다(GEAR_PARK 수신). **시제품은 현재 API를 유지하고, 출시 전 GEAR_SELECTION 전환을 검토한다.**
- 와이퍼 `WiperState`는 OFF/ON만 구분한다. 버추얼 콘솔에서 Low와 High는 둘 다 ON으로 들어온다. 속도 단계(`WiperSwitch`)는 `getCurrentWiperSwitch`와 `checkWiperSwitchCapabilities`만 있고 구독 API가 없다. 그래서 `RAIN_HARD`는 빼고 `RAIN_ON`과 `RAIN_OFF`만 쓴다.
- 버추얼 콘솔의 Seat Temperature 카드는 Driver와 Passenger(1열)만 바꿀 수 있다. **에뮬레이터 콘솔이 앞좌석 열선만 바꿀 수 있어 시연은 앞좌석으로 대체하고, 실제 제품은 뒷좌석 기준이다.** 앱은 모든 좌석을 구독하고 로그에 "뒷좌석/앞좌석 + 좌석 위치"를 남긴다. WARM은 뒷좌석을 우선하고 앞좌석도 인정한다.
- 버추얼 콘솔 Telltales 카드에 Seat Belt 경고등이 있다. SDK의 `Safety.registerTelltales`는 `pleos.car.permission.PRIVATE_CAR_SAFETY` 권한이 필요하다. 이름이 PRIVATE이라 앱 심사 위험이 있어 사용하지 않기로 결정했다.

## Pleos TextToSpeech SDK (음성 합성)

### 사용 버전
- `ai.pleos.playground:TextToSpeech:3.3.1` (2026-11-08 기준 저장소의 최신 버전)
- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (`settings.gradle.kts`에 이미 등록되어 있음)
- 버전 카탈로그: `gradle/libs.versions.toml`의 `ttsSdk`, `pleos-tts-sdk`
- Playground 콘솔에선 TTS SDK가 "준비 중"으로 표시됨. SDK는 Maven 저장소와 공식 문서로 받았다.

### 권한
`AndroidManifest.xml`
```xml
<uses-permission android:name="pleos.car.permission.TTS_SERVICE" />
```

### 초기화 방법 (`KoreanSpeaker.kt`)
1. `TextToSpeech(applicationContext, Mode.ON_DEVICE)`로 생성
2. `initialize(OnInitializeListener)`를 호출하고, `onSuccess()`에서 다음을 실행
   - `addEventListener(EventListener)`: onReady / onStart / onUpdatedRms / onDone / onStop / onError
   - `setLocale(Locale.KOREA)`: 지원 locale은 ko-KR, en-US, fr-CA, es-US, es-MX
3. `speak(text)`로 재생 (기본값 `isFinal = true`)
4. 종료 시 `stop()` → `removeEventListener()` → `release()`

패키지 경로 (AAR에서 직접 확인함):
- `ai.pleos.playground.tts.TextToSpeech`
- `ai.pleos.playground.tts.constant.Mode`, `ai.pleos.playground.tts.constant.ErrorType`
- `ai.pleos.playground.tts.listener.EventListener`, `ai.pleos.playground.tts.listener.OnInitializeListener`

### ON_DEVICE 설정
- 생성자에 `Mode.ON_DEVICE`를 명시한다. 생략하면 기본값 `HYBRID`가 적용되어 서버 연결을 시도한다.
- ON_DEVICE는 네트워크와 앱 등록이 필요 없다. `registerApp()`, Client ID/Secret, `OnServerConnectionListener`는 쓰지 않는다.
- **이 프로젝트에서는 HYBRID와 SERVER 모드, 키를 사용하지 않는다.**

### 에뮬레이터에서 확인한 것 (2026-11-08)
- 로그: `TTS 초기화 성공` → `onReady` → `onStart` → `onDone`
- 재생 중 CaaS 엔진(`ai.pleos.playground.caas`)의 AudioTrack이 started 상태 (USAGE_MEDIA / CONTENT_TYPE_SPEECH)
- 처음 측정에서 onStart부터 onDone까지 38초가 나왔지만 재현되지 않았다. 재측정(2026-11-08, 32자 문장)에서는 onStart→onDone 5.4~8.0초였고, onDone은 실제 AudioTrack 종료와 0.3초 이내로 맞았다. 사용자가 귀로 들은 재생 길이도 5초 이내였다.
- 재생 중에 speak()를 다시 부르면 이전 발화가 끊기고, 끊긴 발화에는 onDone도 onStop도 오지 않는다. 그래서 이어 읽기는 앱에서 큐로 관리하고, onDone을 받은 뒤에 다음 문장을 speak()한다.
- 재생 중 잡음: 시스템 알림음과 알람음에서도 같은 출력(AudioOut_D)의 underrun이 비슷한 비율로 늘어난다. 에뮬레이터와 호스트 쪽 오디오 문제로 보인다 (SDK 출력 문제 아님).

### 표준 `android.speech.tts.TextToSpeech`를 쓰지 않는 이유
- 에뮬레이터에서 초기화가 `status=-1`로 실패하고, 앱에서 보이는 엔진 목록이 비어 있다(`engines == []`).
- `<queries>`, `TTS_SERVICE` 권한 추가, 엔진 패키지 직접 지정을 모두 시도해도 실패했다.
- 추정 원인: CaaS SpeechService의 TTS_SERVICE 인텐트 필터에 `CATEGORY_DEFAULT`가 없어, 표준 TextToSpeech가 엔진을 찾지 못한다.

### 대체 경로
Pleos TTS가 실패하면 미리 녹음한 음성 파일 재생으로 전환한다 (작업지시서 2장 참고).
