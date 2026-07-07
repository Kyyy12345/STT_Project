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
                                          Step1: STT (temperature 0.0)
                                          Step2: 번역 (temperature 0.3)
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
| conversationId | String | 대화 세션 ID (UI에서 자동 생성) |

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

브라우저에서 `http://localhost:8080` 접속

**1. 오디오 파일 생성 (TTS)**
텍스트 입력 → "오디오 파일 생성" 클릭 → `tts_output.wav` 자동 다운로드

**2. STT + 번역 (브라우저 UI)**
오디오 파일 첨부 → "변환 + 번역 요청" 클릭 → 한국어 / 영어 결과 카드로 확인

---

## 핵심 구현 포인트

### STT + 번역 2-Step 처리
단일 프롬프트로 처리 시 STT 정확도가 떨어지는 문제를 해결하기 위해 두 단계로 분리합니다.
- **Step 1 (STT):** `temperature=0.0`으로 오디오를 텍스트로 전사. 어미·조사 추측 금지, 불명확한 부분은 `[unclear]` 표시
- **Step 2 (번역):** `temperature=0.3`으로 전사된 텍스트를 영어로 번역

### TTS (Google GenAI SDK 직접 사용)
Spring AI 2.0.0의 `GoogleGenAiChatOptions`는 오디오 출력 모달리티를 지원하지 않아, `google-genai` SDK를 직접 사용합니다. Gemini가 반환하는 raw PCM 데이터에 WAV 헤더를 직접 생성해 첨부합니다.

### ChatMemory
`MessageWindowChatMemory`(인메모리)를 `MessageChatMemoryAdvisor`로 등록해 대화 문맥을 유지합니다.

### 브라우저 UI (index.html)
- **자동 세션 ID:** `localStorage`에 `user-xxxxxxxx` 형식의 고유 ID를 저장해 새로고침해도 동일 사용자로 인식됩니다. 사용자는 ID를 직접 입력할 필요가 없습니다.
- **결과 카드 UI:** JSON 대신 `한국어 / English` 배지가 붙은 카드 형태로 결과를 표시합니다.

---

## 트러블슈팅

### 1. `InMemoryChatMemory` 클래스 없음
- **원인:** Spring AI 2.0.0에서 `InMemoryChatMemory`가 제거됨
- **해결:** `MessageWindowChatMemory.builder().build()`로 교체

```java
// Before
new MessageChatMemoryAdvisor(new InMemoryChatMemory())

// After
MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build()
```

---

### 2. `MessageChatMemoryAdvisor` import 경로 변경
- **원인:** Spring AI 2.0.0에서 패키지 이동
- **해결:** import 경로 수정

```java
// Before (Spring AI 1.x)
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;

// After (Spring AI 2.0.0)
import org.springframework.ai.chat.memory.MessageChatMemoryAdvisor;
```

---

### 3. `Current request is not a multipart request`
- **원인:** Bruno에서 Body 타입이 `multipart/form-data`로 설정되지 않았거나, `Content-Type: application/json` 헤더가 수동으로 지정된 경우
- **해결:**
  1. Bruno Body 탭 → **Multipart Form** 선택
  2. Headers 탭에서 수동으로 지정한 `Content-Type` 헤더 삭제

---

### 4. 모델명 오류 (404 Not Found)
- **원인:** `gemini-3.1-flash-lite`, `gemini-2.1-flash-lite`, `gemini-1.5-flash` 등 존재하지 않거나 `v1beta` API에서 지원 종료된 모델명 사용
- **해결:** `spring.ai.google.genai.chat.model: gemini-2.5-flash` 사용

```yaml
# 사용 불가
model: gemini-3.1-flash-lite   # 존재하지 않음
model: gemini-1.5-flash         # v1beta API 미지원

# 사용 가능
model: gemini-2.5-flash
```

---

### 5. `429 Quota Exceeded (limit: 0)`
- **원인:** `gemini-2.0-flash` 모델이 해당 API 키의 무료 티어에서 일일 한도가 0으로 제한됨
- **해결:** `gemini-2.5-flash`로 교체. [Google AI Studio](https://aistudio.google.com)에서 API 키 사용 현황 확인 가능

---

### 6. index.html TTS 오디오 파일이 무음
- **원인:** `speechSynthesis.speak()`는 브라우저 스피커로 직접 출력되어 `AudioContext`를 거치지 않음. `MediaRecorder`가 `createMediaStreamDestination()`에서 오디오를 캡처하지 못함
- **해결:** 클라이언트 캡처 방식 폐기 → **서버 사이드 TTS** 구현. Google GenAI SDK로 `gemini-2.5-flash-preview-tts`를 직접 호출해 PCM 오디오를 받아 WAV로 변환 후 반환

---

### 7. `Optional.get()` 체인 오류 (TTS 응답 파싱)
- **원인:** `GenerateContentResponse`의 `candidates()`, `content()`, `parts()`가 모두 `Optional`을 반환하는데, `.get(index)` 앞에 `.get()`(Optional 언래핑)을 누락
- **해결:** 각 단계마다 Optional 언래핑 추가

```java
// Before (오류)
response.candidates().get(0).content().parts().get(0).inlineData().get().data().get()

// After (수정)
response.candidates().get()   // Optional<List<Candidate>> 언래핑
        .get(0)
        .content().get()       // Optional<Content> 언래핑
        .parts().get()         // Optional<List<Part>> 언래핑
        .get(0)
        .inlineData().get()
        .data().get()
```

---

### 8. `ChatClientRequestSpec.options()` 컴파일 오류
- **원인:** Spring AI 2.0.0에서 `options()`는 완성된 `ChatOptions` 객체가 아닌 **Builder** 인스턴스를 인자로 받음
- **해결:** `.build()` 제거

```java
// Before (컴파일 오류)
.options(GoogleGenAiChatOptions.builder().temperature(0.0).build())

// After (정상)
.options(GoogleGenAiChatOptions.builder().temperature(0.0))
```

---

## 향후 개선 계획

### 기능 확장

| 우선순위 | 항목 | 설명 |
|---------|------|------|
| 높음 | **번역 대상 언어 선택** | 현재 영어 고정 → `targetLanguage` 파라미터 추가로 다국어 번역 지원 |
| 높음 | **신뢰도 점수 반환** | `SttTranslationResult`에 `confidence` 필드 추가, 프롬프트에서 확신도 요청 |
| 중간 | **언어 자동 감지** | 한국어 외 입력도 자동 인식 후 번역 (현재 한국어 고정 가정) |
| 중간 | **TTS 보이스 선택** | `voiceName` 파라미터로 Kore 외 다른 목소리 선택 가능하게 |
| 낮음 | **STT 결과 이력 저장** | DB 연동으로 변환 이력 조회 API 추가 |
| 낮음 | **오디오 요약 기능** | 긴 오디오 전사 후 핵심 내용 요약 필드(`summary`) 추가 |

### 성능·안정성

| 우선순위 | 항목 | 설명 |
|---------|------|------|
| 높음 | **비동기 처리** | 대용량 파일 처리를 위한 `@Async` 적용 및 jobId 기반 폴링 API |
| 중간 | **파일 포맷 검증 강화** | MIME type 외 실제 파일 헤더(magic bytes) 검증으로 위장 파일 방어 |
| 중간 | **오류 응답 표준화** | `@ExceptionHandler`로 에러 코드·메시지 일관성 확보 |
| 낮음 | **캐시 적용** | 동일 텍스트 TTS 요청 시 결과 캐시로 API 호출 최소화 |
