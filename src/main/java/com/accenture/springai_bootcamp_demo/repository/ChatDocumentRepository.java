package com.accenture.springai_bootcamp_demo.repository;

import com.accenture.springai_bootcamp_demo.entity.ChatDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatDocumentRepository extends JpaRepository<ChatDocument, Long> {

    List<ChatDocument> findAllByChatIdOrderByCreatedAtDesc(String chatId);

    boolean existsByChatId(String chatId);
}