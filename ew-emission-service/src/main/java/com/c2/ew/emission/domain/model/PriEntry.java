package com.c2.ew.emission.domain.model;

public record PriEntry(
    String priTipi,
    Double minPriMicroSec,
    Double maxPriMicroSec
) {}
