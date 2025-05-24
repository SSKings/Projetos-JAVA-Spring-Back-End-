package com.sskings.voice_transcription.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sskings.voice_transcription.service.TranscriptionService;

@RestController
@RequestMapping("/ai/transcribe")
public class TranscriptionController {
    
    private final TranscriptionService service;

    public TranscriptionController(TranscriptionService service){
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<String> transcribeAudio(@RequestParam("file") MultipartFile file){
        try {
            String transcription = service.TranscribeAudio(file);
            return ResponseEntity.ok(transcription);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Error processing the audio file: " + e.getMessage());
        }
    }
}
