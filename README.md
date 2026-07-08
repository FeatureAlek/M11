# SpringAI Bootcamp Chatbot

A Spring Boot chat app powered by a local LLM via [Ollama](https://ollama.com), with document upload and retrieval-augmented generation (RAG).

## What it does

- Create, list, and delete chat conversations with persisted message history.
- Upload documents (plain text/code) into a chat — chunked, embedded, and stored in a vector store, scoped to that chat only.
- When a message is sent, relevant excerpts from that chat's documents are automatically retrieved and used to ground the model's answer.

## Multi-agent pipeline

1. **Retriever agent** (`RagAgentService`) — searches the vector store for the chat's documents, then makes a separate LLM call to distill matches into a short digest.
2. **Responder agent** (`OpenRouterClient`/Ollama) — answers using the conversation history plus the digest as extra context. Only the user message and final reply are persisted; the digest itself is not.

## Document ingestion

- Fixed-size sliding window chunking (1000 chars, 150 overlap), no token-aware splitting.
- Supports plain UTF-8 text only (code files, `.txt`, `.md`, `.json`, `.csv`, etc.) — no PDF/Word/image parsing yet.
- Max upload size: 10 MB.

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/chats` | List chats |
| `POST` | `/api/chats` | Create a chat |
| `GET` | `/api/chats/{chatId}` | Get chat + history |
| `DELETE` | `/api/chats/{chatId}` | Delete a chat |
| `POST` | `/api/chats/{chatId}/chatMessages` | Send a message |
| `POST` | `/api/chats/{chatId}/documents` | Upload & index a document |
| `GET` | `/api/chats/{chatId}/documents` | List a chat's documents |

## Known limitations

- No parsing for non-text file formats.
- Retriever→responder pipeline is a fixed sequence, not dynamic orchestration.
- Upload confirmations shown in the UI aren't persisted in chat history.