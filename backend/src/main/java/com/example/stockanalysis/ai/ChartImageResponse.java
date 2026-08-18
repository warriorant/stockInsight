package com.example.stockanalysis.ai;

public record ChartImageResponse(
        String period,
        byte[] imageBytes,
        String filename,
        String contentType
) {
}
