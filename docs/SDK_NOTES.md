# SDK 메모

에뮬레이터: Pleos Connect v2.1.0 (Intel x86_64, API 34, Cold boot, RAM 4GB)

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
