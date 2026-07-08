package com.accenture.springai_bootcamp_demo.service;

import com.accenture.springai_bootcamp_demo.entity.Chat;
import com.accenture.springai_bootcamp_demo.entity.ChatDocument;
import com.accenture.springai_bootcamp_demo.repository.ChatDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Splits an uploaded file (code or plain text) into overlapping chunks,
 * embeds and stores them in the {@link VectorStore} tagged with the owning
 * chat's id, and records a lightweight metadata row via
 * {@link ChatDocumentRepository} so uploads can be listed later.
 *
 * <p>A simple fixed-size character sliding window is used for chunking. It
 * is intentionally basic (no token-aware splitting) to keep this bootcamp
 * project free of unnecessary third-party dependencies; swap in a more
 * sophisticated splitter later if needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 150;

    public static final String METADATA_CHAT_ID = "chatId";
    public static final String METADATA_DOCUMENT_ID = "documentId";
    public static final String METADATA_FILENAME = "filename";

    private final VectorStore vectorStore;
    private final ChatDocumentRepository chatDocumentRepository;

    @Transactional
    public ChatDocument ingest(Chat chat, MultipartFile file) {
        String content = readContent(file);
        List<String> chunks = splitIntoChunks(content);

        String documentId = UUID.randomUUID().toString();
        List<Document> documents = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            documents.add(new Document(chunk, Map.of(
                    METADATA_CHAT_ID, chat.getId(),
                    METADATA_DOCUMENT_ID, documentId,
                    METADATA_FILENAME, file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename()
            )));
        }

        vectorStore.add(documents);

        ChatDocument chatDocument = ChatDocument.of(
                chat,
                file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename(),
                chunks.size()
        );
        chatDocumentRepository.save(chatDocument);

        log.info("Indexed document '{}' for chat {} into {} chunks", chatDocument.getFilename(), chat.getId(), chunks.size());
        return chatDocument;
    }

    public List<ChatDocument> listForChat(String chatId) {
        return chatDocumentRepository.findAllByChatIdOrderByCreatedAtDesc(chatId);
    }

    private String readContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read uploaded file: " + ex.getMessage(), ex);
        }
    }

    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        int length = content.length();
        if (length == 0) {
            return chunks;
        }

        int start = 0;
        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);
            chunks.add(content.substring(start, end));
            if (end == length) {
                break;
            }
            start = end - CHUNK_OVERLAP;
        }
        return chunks;
    }
}