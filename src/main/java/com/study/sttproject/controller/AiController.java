package com.study.sttproject.controller;

import com.study.sttproject.dto.SttTranslationResult;
import com.study.sttproject.service.MultimodalService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AiController {

    private final MultimodalService multimodalService;

    public AiController(MultimodalService multimodalService) {
        this.multimodalService = multimodalService;
    }

    @PostMapping("/api/audio-translate")
    public SttTranslationResult audioTranslate(
            @RequestParam MultipartFile file,
            @RequestParam String conversationId) {
        return multimodalService.transcribeAndTranslate(file, conversationId);
    }

    @GetMapping("/api/tts")
    public ResponseEntity<byte[]> textToSpeech(@RequestParam String text) {
        byte[] audioData = multimodalService.textToSpeech(text);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tts_output.wav\"")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(audioData);
    }
}
