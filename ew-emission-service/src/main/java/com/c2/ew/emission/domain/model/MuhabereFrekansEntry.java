package com.c2.ew.emission.domain.model;

import com.c2.ew.emission.domain.enums.MuhabereFrekansTipi;

public record MuhabereFrekansEntry(
    MuhabereFrekansTipi frekansTipi,
    Double minFrekansMhz,
    Double maxFrekansMhz
) {}
