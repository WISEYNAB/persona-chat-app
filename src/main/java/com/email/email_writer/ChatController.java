package com.email.email_writer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/message")
    public ResponseEntity<String> sendMessage(@RequestBody ChatRequest chatRequest) {
        String response = chatService.generateChatResponse(chatRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory() {
        return ResponseEntity.ok(chatService.getChatHistory());
    }
}