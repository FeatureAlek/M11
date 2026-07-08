package com.accenture.springai_bootcamp_demo.service;

import com.accenture.springai_bootcamp_demo.client.OpenRouterClient;
import com.accenture.springai_bootcamp_demo.dto.ChatDocumentDto;
import com.accenture.springai_bootcamp_demo.dto.ChatDto;
import com.accenture.springai_bootcamp_demo.dto.ChatSummaryDto;
import com.accenture.springai_bootcamp_demo.dto.CreateChatRequest;
import com.accenture.springai_bootcamp_demo.dto.SendMessageRequest;
import com.accenture.springai_bootcamp_demo.entity.Chat;
import com.accenture.springai_bootcamp_demo.entity.ChatDocument;
import com.accenture.springai_bootcamp_demo.entity.ChatMessage;
import com.accenture.springai_bootcamp_demo.entity.Role;
import com.accenture.springai_bootcamp_demo.mapper.ChatMapper;
import com.accenture.springai_bootcamp_demo.repository.ChatRepository;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates chat use-cases: creating, reading and deleting conversations,
 * exchanging chatMessages with the AI model, and managing per-chat uploaded
 * documents used for retrieval-augmented generation (RAG).
 *
 * <p>{@link #sendMessage} implements a small multi-agent pipeline:
 * <ol>
 *   <li><b>Retriever agent</b> ({@link RagAgentService}) - searches the
 *       chat's indexed documents and condenses relevant excerpts into a
 *       short digest, only if the chat has documents attached;</li>
 *   <li><b>Responder agent</b> ({@link OpenRouterClient}, backed by Ollama)
 *       - answers the user using the conversation history plus the digest
 *       (if any) as extra system context.</li>
 * </ol>
 */
@Slf4j
@Service
@AllArgsConstructor
public class ChatService {
    private final ChatRepository chatRepository;
    private final OpenRouterClient openRouterClient;
    private final ChatMapper chatMapper;
    private final DocumentIngestionService documentIngestionService;
    private final RagAgentService ragAgentService;

    @Transactional
    public ChatDto createChat(CreateChatRequest request) {
        Chat chat = Chat.create(ChatTitles.resolveInitial(request.title()));
        chatRepository.save(chat);
        log.info("Created chat {}", chat.getId());
        return chatMapper.toDto(chat);
    }

    @Transactional(readOnly = true)
    public List<ChatSummaryDto> listChats() {
        return chatMapper.toSummaries(chatRepository.findAllByOrderByUpdatedAtDesc());
    }

    @Transactional(readOnly = true)
    public ChatDto getChat(String chatId) {
        return chatMapper.toDto(loadChat(chatId));
    }

    @Transactional
    public void deleteChat(String chatId) {
        if (!chatRepository.existsById(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
        chatRepository.deleteById(chatId);
        log.info("Deleted chat {}", chatId);
    }

    /**
     * Persists the user message, optionally augments the model call with a
     * RAG context digest built from this chat's uploaded documents, asks the
     * model for a reply, stores it and returns the refreshed conversation.
     */
    @Transactional
    public ChatDto sendMessage(String chatId, SendMessageRequest request) {
        Chat chat = loadChat(chatId);

        recordUserMessage(chat, request.content());

        String contextDigest = ragAgentService.buildContextDigest(chatId, request.content());
        List<ChatMessage> promptMessages = withContext(chat.getChatMessages(), contextDigest);

        String reply = openRouterClient.complete(promptMessages);
        recordAssistantMessage(chat, reply);

        chatRepository.save(chat);
        return chatMapper.toDto(chat);
    }

    /**
     * Uploads and indexes a document for later RAG retrieval, scoped to this
     * chat only.
     */
    @Transactional
    public ChatDocumentDto uploadDocument(String chatId, MultipartFile file) {
        Chat chat = loadChat(chatId);
        ChatDocument document = documentIngestionService.ingest(chat, file);
        return toDocumentDto(document);
    }

    @Transactional(readOnly = true)
    public List<ChatDocumentDto> listDocuments(String chatId) {
        if (!chatRepository.existsById(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
        return documentIngestionService.listForChat(chatId).stream()
                .map(ChatService::toDocumentDto)
                .toList();
    }

    /**
     * Builds a transient message list for the model call: the digest (if
     * any) is prepended as a SYSTEM message but is never persisted to the
     * chat's own history, keeping the stored conversation clean.
     */
    private List<ChatMessage> withContext(List<ChatMessage> history, String contextDigest) {
        if (contextDigest == null || contextDigest.isBlank()) {
            return history;
        }
        List<ChatMessage> augmented = new ArrayList<>(history.size() + 1);
        augmented.add(ChatMessage.of(Role.SYSTEM,
                "Use the following context from the user's uploaded documents if relevant:\n" + contextDigest));
        augmented.addAll(history);
        return augmented;
    }

    private void recordUserMessage(Chat chat, String content) {
        chat.addMessage(ChatMessage.of(Role.USER, content));
        if (ChatTitles.isPlaceholder(chat.getTitle())) {
            chat.setTitle(ChatTitles.fromFirstMessage(content));
        }
    }

    private void recordAssistantMessage(Chat chat, String content) {
        chat.addMessage(ChatMessage.of(Role.ASSISTANT, content));
    }

    private Chat loadChat(String chatId) {
        return chatRepository.findWithMessagesById(chatId)
                .orElseThrow(() -> new ChatNotFoundException(chatId));
    }

    private static ChatDocumentDto toDocumentDto(ChatDocument document) {
        return new ChatDocumentDto(
                document.getId(),
                document.getFilename(),
                document.getChunkCount(),
                document.getCreatedAt()
        );
    }
}