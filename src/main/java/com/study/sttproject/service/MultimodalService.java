package com.study.sttproject.service;

import com.google.genai.Client;
import com.google.genai.types.*;
import com.study.sttproject.dto.SttTranslationResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

@Service
public class MultimodalService {

    private final ChatClient chatClient;

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    public MultimodalService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public SttTranslationResult transcribeAndTranslate(MultipartFile file, String conversationId) {
        validateAudio(file);
        ByteArrayResource resource = toResource(file);
        MimeType mimeType = MimeType.valueOf(file.getContentType());

        // Step 1: STT - 오디오를 텍스트로 전사 (temperature 0.0 - 추측 없이 정확히)
        String originalText = chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder().temperature(0.0))
                .user(u -> u.text("""
                        이 오디오에서 들리는 말을 텍스트로 전사하세요.
                        규칙:
                        - 들리는 내용을 한 글자도 바꾸지 말고 그대로 적으세요.
                        - 어미, 조사, 종결어미를 절대 추측하거나 바꾸지 마세요.
                        - 확실하지 않은 부분은 [unclear]로 표시하세요.
                        - 전사된 텍스트만 반환하고 다른 설명은 하지 마세요.
                        """)
                        .media(mimeType, resource))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        if (originalText == null || originalText.isBlank()) {
            throw new RuntimeException("STT 결과가 비어있습니다.");
        }

        // Step 2: 번역 - 전사된 텍스트를 영어로 번역 (temperature 0.3)
        String translatedText = chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder().temperature(0.3))
                .user("다음 텍스트를 영어로 번역하세요. 번역문만 반환하고 다른 설명은 하지 마세요.\n\n" + originalText)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        return new SttTranslationResult(
                originalText.trim(),
                translatedText != null ? translatedText.trim() : "");
    }

    private void validateAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 없습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("audio/")) {
            throw new IllegalArgumentException("오디오 파일만 허용됩니다.");
        }
    }

    private ByteArrayResource toResource(MultipartFile file) {
        try {
            return new ByteArrayResource(file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("파일 읽기 실패", e);
        }
    }

    public byte[] textToSpeech(String text) {
        Client client = Client.builder().apiKey(apiKey).build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities("AUDIO")
                .speechConfig(SpeechConfig.builder()
                        .voiceConfig(VoiceConfig.builder()
                                .prebuiltVoiceConfig(PrebuiltVoiceConfig.builder()
                                        .voiceName("Kore")
                                        .build())
                                .build())
                        .languageCode("ko-KR")
                        .build())
                .build();

        GenerateContentResponse response = client.models.generateContent(
                "gemini-2.5-flash-preview-tts",
                List.of(Content.fromParts(Part.fromText(text))),
                config
        );

        byte[] pcmData = response.candidates().get()   // Optional<List<Candidate>>
                .get(0)                                  // List index
                .content().get()                         // Optional<Content>
                .parts().get()                           // Optional<List<Part>>
                .get(0)                                  // List index
                .inlineData().get()                      // Optional<Blob>
                .data().get();                           // Optional<byte[]>

        return toWav(pcmData, 24000, 1, 16);
    }

    private byte[] toWav(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        ByteBuffer buf = ByteBuffer.allocate(44 + pcmData.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes());
        buf.putInt(36 + pcmData.length);
        buf.put("WAVE".getBytes());
        buf.put("fmt ".getBytes());
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(byteRate);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);
        buf.put("data".getBytes());
        buf.putInt(pcmData.length);
        buf.put(pcmData);
        return buf.array();
    }
}
