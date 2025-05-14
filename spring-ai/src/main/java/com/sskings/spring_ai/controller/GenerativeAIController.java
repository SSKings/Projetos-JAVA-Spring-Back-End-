package com.sskings.spring_ai.controller;

import org.springframework.web.bind.annotation.RestController;

import com.sskings.spring_ai.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
public class GenerativeAIController {

    private final ChatService chatService;

    public GenerativeAIController(ChatService chatService){
        this.chatService = chatService;
    }
    
    @GetMapping("ask-ai")
    public String getResponse(@RequestParam String prompt) {
        return chatService.getResponse(prompt);
    }

    @GetMapping("ask-ai-options")
    public String getResponseWithOptions(@RequestParam String prompt) {
        return chatService.getResponseWithOptions(prompt);
    }
    
    
}