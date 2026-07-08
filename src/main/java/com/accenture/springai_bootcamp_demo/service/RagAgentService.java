package com.accenture.springai_bootcamp_demo.service;

import com.accenture.springai_bootcamp_demo.repository.ChatDocumentRepository;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaOptions;
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
        If the excerpts contain ANY fact that directly answers or relates to the question,
        include it in the digest, even if phrased differently than the question.
        Only respond with exactly NO_RELEVANT_CONTEXT if the excerpts are about a
        completely unrelated topic.
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
        log.info("[RAG-DEBUG] buildContextDigest called for chat {} with query '{}'", chatId, userQuery);

        if (!chatDocumentRepository.existsByChatId(chatId)) {
            log.info("[RAG-DEBUG] No documents exist for chat {}, skipping RAG", chatId);
            return null;
        }

        List<Document> matches = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuery)
                        .topK(TOP_K)
                        .filterExpression(METADATA_CHAT_ID + " == '" + chatId + "'")
                        .build()
        );

        log.info("[RAG-DEBUG] similaritySearch returned {} matches", matches.size());
        for (Document match : matches) {
            log.info("[RAG-DEBUG] match text (first 100 chars): {}",
                    match.getText().substring(0, Math.min(100, match.getText().length())));
        }

        if (matches.isEmpty()) {
            log.info("[RAG-DEBUG] No matches found, returning null");
            return null;
        }

        String rawExcerpts = matches.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        String digest = chatClient.prompt()
                .system(DIGEST_SYSTEM_PROMPT)
                .user("Question: " + userQuery + "\n\nExcerpts:\n" + rawExcerpts)
                .options(OllamaOptions.builder().temperature(0.0).build())
                .call()
                .content();

        log.info("[RAG-DEBUG] digest LLM call returned: '{}'", digest);

        if (!StringUtils.hasText(digest) || digest.contains("NO_RELEVANT_CONTEXT")) {
            log.info("[RAG-DEBUG] digest was blank or NO_RELEVANT_CONTEXT, returning null");
            return null;
        }

        log.info("[RAG-DEBUG] Built RAG context digest for chat {} ({} chars)", chatId, digest.length());
        return digest.trim();
    }
}