package com.c2.ew.copilot.dto;


public record MuhabereFrekansEntry(
    MuhabereFrekansTipi frekansTipi,
    Double minFrekansMhz,
    Double maxFrekansMhz
) {}
