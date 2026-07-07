# STT-Project 파일별 주요 기능 정리

---

## config/ChatConfig.java

**역할:** Spring AI `ChatClient` 빈을 생성하고 대화 메모리를 설정

```java
@Configuration
public class ChatConfig {
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(
                MessageWindowChatMemory.builder().build()).build())
            .build();
    }
}
```

| 포인트 | 설명 |
|--------|------|
| `ChatClient.Builder` | Spring AI가 자동 주입하는 빌더. `spring-ai-starter-model-google-genai` 의존성이 있으면 Gemini 모델로 자동 구성됨 |
| `MessageWindowChatMemory` | Spring AI 2.0.0에서 `InMemoryChatMemory`를 대체. 최근 N개 메시지를 인메모리에 유지 |
| `MessageChatMemoryAdvisor` | 모든 요청에 대화 이력을 자동으로 컨텍스트에 추가하는 어드바이저 |

---

## controller/AiController.java

**역할:** REST 엔드포인트 노출, 요청 수신 후 서비스 위임

### 엔드포인트 목록

#### `POST /api/audio-translate`
```
파라미터: file (MultipartFile), conversationId (String)
응답: SttTranslationResult { originalText, translatedText }
```
- 오디오 파일과 대화 ID를 받아 STT + 번역 결과를 JSON으로 반환
- `Content-Type: multipart/form-data` 필수

#### `GET /api/tts`
```
파라미터: text (String)
응답: audio/wav 파일 (Content-Disposition: attachment)
```
- 텍스트를 입력받아 Gemini TTS로 생성한 WAV 파일을 다운로드로 반환
- `ResponseEntity<byte[]>`로 바이너리 응답 처리

---

## dto/SttTranslationResult.java

**역할:** STT + 번역 결과를 담는 응답 DTO

```java
public record SttTranslationResult(
    String originalText,    // 오디오에서 전사된 원문
    String translatedText   // 영어 번역 결과
) {}
```

| 포인트 | 설명 |
|--------|------|
| Java `record` | 불변 객체. getter, equals, hashCode, toString 자동 생성 |
| Spring AI `.entity()` 연동 | `chatClient.call().entity(SttTranslationResult.class)` 호출 시 AI 응답 JSON을 자동으로 이 record에 역직렬화 |

---

## service/MultimodalService.java

**역할:** STT+번역(2-step)과 TTS의 핵심 비즈니스 로직

### `transcribeAndTranslate(MultipartFile, String)`

2단계로 처리해 STT 정확도를 높임

```
Step 1 - STT (temperature=0.0)
  오디오 파일 + 전사 전용 프롬프트
  → 어미·조사 추측 금지, [unclear] 표시
  → originalText

Step 2 - 번역 (temperature=0.3)
  originalText + 번역 프롬프트
  → translatedText
```

| 포인트 | 설명 |
|--------|------|
| `.options(GoogleGenAiChatOptions.builder().temperature(x))` | Spring AI 2.0.0에서 `options()`는 Builder를 그대로 전달 (`.build()` 호출 금지) |
| `.media(mimeType, resource)` | 오디오 파일을 멀티모달 입력으로 Gemini에 전달 |
| `ChatMemory.CONVERSATION_ID` | 어드바이저에 대화 ID를 주입해 세션별 대화 이력 유지 |

---

### `textToSpeech(String)`

Google GenAI SDK를 직접 사용해 TTS 생성

```
텍스트 입력
  → GenerateContentConfig (responseModalities=AUDIO, voice=Kore, lang=ko-KR)
  → gemini-2.5-flash-preview-tts 호출
  → PCM 바이너리 추출 (inlineData → data)
  → WAV 헤더 생성 (24000Hz, 16bit, Mono)
  → byte[] 반환
```

| 포인트 | 설명 |
|--------|------|
| SDK 직접 사용 이유 | Spring AI 2.0.0 `GoogleGenAiChatOptions`에 `responseModalities` 옵션이 없어 `google-genai` SDK를 직접 호출 |
| `response.candidates().get().get(0).content().get()...` | SDK 응답의 모든 필드가 `Optional`로 래핑되어 있어 각 단계마다 `.get()` 언래핑 필요 |
| `toWav(pcmData, 24000, 1, 16)` | Gemini TTS는 raw PCM(L16)을 반환하므로 브라우저에서 재생 가능한 WAV 형식으로 44바이트 헤더를 직접 생성 |

---

### `validateAudio(MultipartFile)`
- `file`이 null이거나 비어 있으면 예외
- `ContentType`이 `audio/`로 시작하지 않으면 예외

### `toResource(MultipartFile)`
- `MultipartFile`의 바이트 배열을 `ByteArrayResource`로 변환해 Spring AI `media()` API에 전달 가능한 형태로 변환

---

## resources/application.yaml

**역할:** 애플리케이션 전역 설정

```yaml
spring:
  ai:
    google:
      genai:
        chat:
          model: gemini-2.5-flash   # STT·번역에 사용하는 모델
        api-key: ${GOOGLE_API_KEY}  # 환경변수에서 주입
  servlet:
    multipart:
      max-file-size: 10MB           # 오디오 파일 최대 크기
      max-request-size: 10MB
```

| 포인트 | 설명 |
|--------|------|
| `${GOOGLE_API_KEY}` | 하드코딩 방지. 환경 변수 또는 IDE Run Config에서 설정 |
| TTS 모델 별도 설정 없음 | TTS는 SDK를 직접 호출하므로 yaml에 설정하지 않고 코드에서 `gemini-2.5-flash-preview-tts`로 지정 |

---

## resources/static/index.html

**역할:** 브라우저에서 텍스트를 입력하면 서버 TTS API를 호출해 WAV 파일을 생성·다운로드

### 동작 흐름

```
텍스트 입력 → [오디오 파일 생성] 버튼 클릭
  → fetch('/api/tts?text=...')
  → 서버에서 WAV 생성
  → Blob URL 생성 → <a> 태그로 자동 다운로드
  → tts_output.wav 저장
```

| 포인트 | 설명 |
|--------|------|
| 서버 사이드 TTS 방식 채택 이유 | `speechSynthesis.speak()`는 브라우저 스피커로 직접 출력되어 Web Audio API로 캡처 불가. 클라이언트 녹음 방식은 무음 파일이 생성됨 |
| `encodeURIComponent(text)` | 한글 등 멀티바이트 문자를 URL 파라미터로 안전하게 인코딩 |
| `URL.createObjectURL` / `revokeObjectURL` | 메모리 누수 방지를 위해 다운로드 후 즉시 URL 해제 |
