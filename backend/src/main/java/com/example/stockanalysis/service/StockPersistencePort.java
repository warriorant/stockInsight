package com.example.stockanalysis.service;

import com.example.stockanalysis.dto.ChartPatternAnalysisResponse;
import com.example.stockanalysis.dto.StockCandleResponse;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockPersistencePort {

    void saveStockMaster(Collection<StockDefinition> stocks);

    List<StockDefinition> findStocks();

    List<StockDefinition> searchStocks(String keyword, int limit);

    Optional<StockDefinition> findStock(String symbol);

    void saveCandles(String symbol, List<StockCandleResponse> candles, String source, boolean adjusted);

    List<StockCandleResponse> findCandles(String symbol, LocalDate startDate, LocalDate endDate, String source);

    long countCandles(String symbol, String source);

    void saveChartPatternAnalysis(ChartPatternAnalysisResponse response);

    Optional<ChartPatternAnalysisResponse> findChartPatternAnalysis(String symbol, LocalDate targetDate);

    Optional<ChartPatternAnalysisResponse> findLatestChartPatternAnalysis(String symbol);
}
