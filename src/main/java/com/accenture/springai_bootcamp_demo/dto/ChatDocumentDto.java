package com.accenture.springai_bootcamp_demo.dto;

import java.time.Instant;

public record ChatDocumentDto(
        Long id,
        String filename,
        int chunkCount,
        Instant createdAt
) {
}