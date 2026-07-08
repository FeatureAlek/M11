package com.accenture.springai_bootcamp_demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata record for a document uploaded to a {@link Chat} and indexed into
 * the vector store for retrieval-augmented generation. The actual chunk
 * embeddings live in the vector store, keyed by this document's id via
 * metadata; this row exists so we can list/track what has been uploaded and
 * enforce per-chat scoping.
 */
@Entity
@Table(name = "chat_documents")
@Getter
@Setter
@NoArgsConstructor
public class ChatDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static ChatDocument of(Chat chat, String filename, int chunkCount) {
        ChatDocument document = new ChatDocument();
        document.chat = chat;
        document.filename = filename;
        document.chunkCount = chunkCount;
        document.createdAt = Instant.now();
        return document;
    }
}