package com.accenture.springai_bootcamp_demo.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides an in-memory {@link VectorStore} backed by the auto-configured
 * Ollama {@link EmbeddingModel} (see {@code spring.ai.ollama.embedding.*}).
 * No external vector database is required, which keeps the bootcamp project
 * self-contained. Note: contents are held in memory and are lost on
 * restart; documents must be re-uploaded after the app restarts.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * Spring AI auto-configures {@link ChatClient.Builder} but not a
     * ready-to-inject {@link ChatClient} bean. {@link RagAgentService} (and
     * any other component that just wants a default, no-frills chat client)
     * depends on this bean directly.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}