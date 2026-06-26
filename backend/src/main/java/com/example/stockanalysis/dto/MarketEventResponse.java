package com.example.stockanalysis.dto;

import java.time.LocalDate;
import java.util.List;

public record MarketEventResponse(
        String id,
        String title,
        String category,
        LocalDate scheduledDate,
        String importance,
        String summary,
        String beginnerImpact,
        List<String> relatedSectors,
        List<String> affectedSymbols
) {
}
