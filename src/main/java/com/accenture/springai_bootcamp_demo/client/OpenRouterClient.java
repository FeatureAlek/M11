package com.accenture.springai_bootcamp_demo.client;

import com.accenture.springai_bootcamp_demo.entity.ChatMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Thin client over the local Ollama chat completions API, backed by Spring AI's
 * {@link ChatClient}. Keeps the public surface intentionally small: callers
 * hand over the conversation history and receive the assistant's reply text.
 */
@Slf4j
@Component
public class OpenRouterClient {

    private final ChatClient chatClient;

    public OpenRouterClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String complete(List<ChatMessage> history) {
        requireApiKey();
        String reply = call(history);
        return extractContent(reply);
    }

    private String call(List<ChatMessage> history) {
        try {
            List<Message> messages = toPromptMessages(history);
            Prompt prompt = new Prompt(messages);

            return chatClient.prompt(prompt)
                    .call()
                    .content();
        } catch (OpenRouterException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Ollama request failed", ex);
            throw new OpenRouterException("Failed to reach Ollama: " + ex.getMessage(), ex);
        }
    }

    private List<Message> toPromptMessages(List<ChatMessage> history) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage entry : history) {
            messages.add(toMessage(entry));
        }
        return messages;
    }

    private Message toMessage(ChatMessage entry) {
        String content = entry.getContent();
        return switch (entry.getRole()) {
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            case SYSTEM -> new SystemMessage(content);
        };
    }

    private String extractContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new OpenRouterException("Ollama returned an empty response");
        }
        return content.trim();
    }

    private void requireApiKey() {
        // No-op: Ollama runs locally and does not require an API key.
        // Kept as a hook in case authentication is added later (e.g. remote Ollama server).
    }
}
