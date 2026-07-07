# STT-Project

Spring AI + Google Gemini를 활용한 **오디오 STT(음성→텍스트) + 영어 번역** 웹 애플리케이션입니다.

---

## 기술 스택

| 항목 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Spring AI | 2.0.0 |
| Google GenAI SDK | 1.58.0 |
| Gemini (STT/번역) | gemini-2.5-flash |
| Gemini (TTS) | gemini-2.5-flash-preview-tts |
| Build Tool | Gradle 9.5.1 |

---

## 아키텍처

```
[Browser - index.html]
        │
        │ GET /api/tts?text=...
        ▼
[AiController]
        │
        ├─ textToSpeech()  ──► Google GenAI SDK (gemini-2.5-flash-preview-tts)
        │                       PCM → WAV 변환 후 반환
        │
        │ POST /api/audio-translate
        ▼
[AiController]
        │
        └─ transcribeAndTranslate()  ──► Spring AI ChatClient (gemini-2.5-flash)
                                          멀티모달: 오디오 파일 + 프롬프트
                                          → { originalText, translatedText }
```

---

## 프로젝트 구조

```
src/main/java/com/study/sttproject/
├── SttProjectApplication.java
├── config/
│   └── ChatConfig.java          # ChatClient 빈 등록 (MessageWindowChatMemory 포함)
├── controller/
│   └── AiController.java        # REST 엔드포인트
├── dto/
│   └── SttTranslationResult.java # 응답 DTO (record)
└── service/
    ├── MultimodalService.java    # STT+번역, TTS 비즈니스 로직
    └── TransferService.java

src/main/resources/
├── application.yaml
└── static/
    └── index.html               # TTS 오디오 파일 생성 UI
```

---

## API 명세

### 1. 오디오 STT + 영어 번역

```
POST /api/audio-translate
Content-Type: multipart/form-data
```

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| file | MultipartFile | 오디오 파일 (audio/*) |
| conversationId | String | 대화 세션 ID |

**응답 예시:**
```json
{
  "originalText": "안녕하세요, 오늘 날씨가 좋네요",
  "translatedText": "Hello, the weather is nice today"
}
```

---

### 2. 텍스트 → 오디오 파일 생성 (TTS)

```
GET /api/tts?text={텍스트}
```

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| text | String | 음성으로 변환할 텍스트 |

**응답:** `audio/wav` 파일 다운로드 (24000Hz, 16bit, Mono PCM)

---

## 환경 설정

### 환경 변수

```bash
GOOGLE_API_KEY=your_google_ai_studio_api_key
```

### application.yaml

```yaml
spring:
  ai:
    google:
      genai:
        chat:
          model: gemini-2.5-flash
        api-key: ${GOOGLE_API_KEY}
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

---

## 실행 방법

```bash
# 환경 변수 설정 후
./gradlew bootRun
```

브라우저에서 `http://localhost:8080` 접속 → 텍스트 입력 → 오디오 파일 생성 → Bruno로 STT 테스트

---

## 핵심 구현 포인트

### STT + 번역 (멀티모달)
Spring AI의 `ChatClient`에 오디오 파일을 `media()`로 첨부하고, Gemini에게 STT와 번역을 동시에 요청합니다. `.entity(SttTranslationResult.class)`로 JSON 응답을 DTO에 자동 바인딩합니다.

### TTS (Google GenAI SDK 직접 사용)
Spring AI 2.0.0의 `GoogleGenAiChatOptions`는 오디오 출력 모달리티를 지원하지 않아, `google-genai` SDK를 직접 사용합니다. Gemini가 반환하는 raw PCM 데이터에 WAV 헤더를 직접 생성해 첨부합니다.

### ChatMemory
`MessageWindowChatMemory`(인메모리)를 `MessageChatMemoryAdvisor`로 등록해 대화 문맥을 유지합니다.
