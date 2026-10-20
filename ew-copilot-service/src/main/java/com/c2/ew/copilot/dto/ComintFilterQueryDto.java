package com.c2.ew.copilot.dto;

public record ComintFilterQueryDto(
    String teshisKimlik,
    String tip,
    String lisan,
    String haberlesme,
    Boolean kripto,
    Double minFrekansMhz,
    Double maxFrekansMhz,
    Double minGenlikDbm,
    Double maxGenlikDbm,
    String bolge,
    String protokol,
    String cagriAdi,
    String karsiCagriAdi,
    String kayitTipi,
    String zamanAraligi,
    Integer limit
) {}
