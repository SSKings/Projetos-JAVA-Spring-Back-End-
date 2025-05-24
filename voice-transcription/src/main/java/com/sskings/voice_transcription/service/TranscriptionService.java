package com.sskings.voice_transcription.service;

import java.io.File;
import java.io.IOException;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TranscriptionService {
    
    private final OpenAiAudioTranscriptionModel audioTranscriptionModel;

    public TranscriptionService(@Value("${spring.ai.openai.api-key}") String apiKey){
        OpenAiAudioApi openAiAudioApi = OpenAiAudioApi.builder()
            .apiKey(apiKey)    
            .build();
        this.audioTranscriptionModel = new OpenAiAudioTranscriptionModel(openAiAudioApi);
    }

    public String TranscribeAudio(MultipartFile file) throws IOException {
        File  tempFile = File.createTempFile("audio", ".mp3");
        file.transferTo(tempFile);
        
        OpenAiAudioTranscriptionOptions transcripionOptions = 
            OpenAiAudioTranscriptionOptions.builder()
            .responseFormat(TranscriptResponseFormat.TEXT)
            .language("pt")
            .temperature(0f)    
            .build();

        FileSystemResource audioFile = new FileSystemResource(tempFile);
        AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioFile, transcripionOptions);
        AudioTranscriptionResponse response = audioTranscriptionModel.call(transcriptionRequest);
        
        tempFile.delete();

        return response.getResult().getOutput();
    }
}
