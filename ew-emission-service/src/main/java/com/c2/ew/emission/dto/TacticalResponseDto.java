package com.c2.ew.emission.dto;

import java.util.List;

/**
 * Taktik Operasyonel Yanıt DTO'su (Metinsel Taktik Brifing + LLM Telemetri Logları)
 */
public record TacticalResponseDto(
    String operationalBriefing,
    List<String> executionLogs
) {
    public TacticalResponseDto(String operationalBriefing) {
        this(operationalBriefing, List.of());
    }
}
