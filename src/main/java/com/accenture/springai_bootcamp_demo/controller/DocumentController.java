package com.accenture.springai_bootcamp_demo.controller;

import com.accenture.springai_bootcamp_demo.dto.ChatDocumentDto;
import com.accenture.springai_bootcamp_demo.service.ChatService;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API for uploading and listing documents attached to a chat. Uploaded
 * documents are chunked, embedded, and indexed for retrieval-augmented
 * generation, scoped to that chat only.
 */
@RestController
@RequestMapping("/api/chats/{chatId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatDocumentDto> upload(@PathVariable String chatId,
                                                  @RequestParam("file") MultipartFile file) {
        ChatDocumentDto dto = chatService.uploadDocument(chatId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public List<ChatDocumentDto> list(@PathVariable String chatId) {
        return chatService.listDocuments(chatId);
    }
}