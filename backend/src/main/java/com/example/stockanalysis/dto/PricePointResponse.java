package com.example.stockanalysis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricePointResponse(
        LocalDate date,
        BigDecimal close,
        Long volume
) {
}

