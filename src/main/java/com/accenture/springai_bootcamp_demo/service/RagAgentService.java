package com.accenture.springai_bootcamp_demo.service;

import com.accenture.springai_bootcamp_demo.repository.ChatDocumentRepository;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.accenture.springai_bootcamp_demo.service.DocumentIngestionService.METADATA_CHAT_ID;

/**
 * The "retriever agent" in the multi-agent workflow. Given a chat id and the
 * user's latest message, it:
 * <ol>
 *   <li>runs a similarity search against the vector store, scoped to that
 *       chat's uploaded documents via a metadata filter expression;</li>
 *   <li>hands the raw matching excerpts to a dedicated LLM call (a distinct
 *       system prompt from the main conversation) whose only job is to
 *       distill them into a short, focused digest.</li>
 * </ol>
 * The digest is what actually gets injected into the main conversation
 * (handled separately by {@link ChatService}, the "responder agent"), rather
 * than the raw chunks, which keeps prompts small and on-topic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagAgentService {

    private static final int TOP_K = 4;

    private static final String DIGEST_SYSTEM_PROMPT = """
            You are a context-retrieval agent that supports a separate answering agent.
            You will be given a user's question and a set of raw document excerpts retrieved
            from a vector search. Your only job is to produce a short, focused digest
            (no more than 200 words) containing just the information relevant to answering
            the question. Do not answer the question yourself. Do not add commentary.
            If none of the excerpts are relevant, respond with exactly: NO_RELEVANT_CONTEXT
            """;

    private final VectorStore vectorStore;
    private final ChatDocumentRepository chatDocumentRepository;
    private final ChatClient chatClient;

    /**
     * Returns a condensed context digest for the given chat/query, or
     * {@code null} if the chat has no indexed documents or nothing relevant
     * was found.
     */
    public String buildContextDigest(String chatId, String userQuery) {
        if (!chatDocumentRepository.existsByChatId(chatId)) {
            return null;
        }

        List<Document> matches = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuery)
                        .topK(TOP_K)
                        .filterExpression(METADATA_CHAT_ID + " == '" + chatId + "'")
                        .build()
        );

        if (matches.isEmpty()) {
            return null;
        }

        String rawExcerpts = matches.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        String digest = chatClient.prompt()
                .system(DIGEST_SYSTEM_PROMPT)
                .user("Question: " + userQuery + "\n\nExcerpts:\n" + rawExcerpts)
                .call()
                .content();

        if (!StringUtils.hasText(digest) || digest.contains("NO_RELEVANT_CONTEXT")) {
            return null;
        }

        log.debug("Built RAG context digest for chat {} ({} chars)", chatId, digest.length());
        return digest.trim();
    }
}