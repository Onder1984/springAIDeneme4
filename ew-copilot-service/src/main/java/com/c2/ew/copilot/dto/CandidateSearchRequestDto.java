package com.c2.ew.copilot.dto;

public record CandidateSearchRequestDto(
    String targetId,
    Double minFrekansMhz,
    Double maxFrekansMhz,
    Double minPriMicroSec,
    Double maxPriMicroSec,
    Double minPwMicroSec,
    Double maxPwMicroSec,
    String timeRange,
    Integer limit
) {}
