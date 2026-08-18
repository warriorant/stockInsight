package com.example.stockanalysis.service;

import com.example.stockanalysis.dto.ChartPatternAnalysisResponse;
import com.example.stockanalysis.dto.StockCandleResponse;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NoopStockPersistencePort implements StockPersistencePort {

    @Override
    public void saveStockMaster(Collection<StockDefinition> stocks) {
    }

    @Override
    public List<StockDefinition> findStocks() {
        return List.of();
    }

    @Override
    public List<StockDefinition> searchStocks(String keyword, int limit) {
        return List.of();
    }

    @Override
    public Optional<StockDefinition> findStock(String symbol) {
        return Optional.empty();
    }

    @Override
    public void saveCandles(String symbol, List<StockCandleResponse> candles, String source, boolean adjusted) {
    }

    @Override
    public List<StockCandleResponse> findCandles(String symbol, LocalDate startDate, LocalDate endDate, String source) {
        return List.of();
    }

    @Override
    public long countCandles(String symbol, String source) {
        return 0;
    }

    @Override
    public void saveChartPatternAnalysis(ChartPatternAnalysisResponse response) {
    }

    @Override
    public Optional<ChartPatternAnalysisResponse> findChartPatternAnalysis(String symbol, LocalDate targetDate) {
        return Optional.empty();
    }

    @Override
    public Optional<ChartPatternAnalysisResponse> findLatestChartPatternAnalysis(String symbol) {
        return Optional.empty();
    }
}
