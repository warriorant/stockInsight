package com.example.stockanalysis.service;

import com.example.stockanalysis.domain.ChartPatternAnalysisRun;
import com.example.stockanalysis.domain.ChartPatternPeriodResult;
import com.example.stockanalysis.domain.Stock;
import com.example.stockanalysis.domain.StockCandle;
import com.example.stockanalysis.dto.ChartPatternAnalysisResponse;
import com.example.stockanalysis.dto.PatternBacktestMetricResponse;
import com.example.stockanalysis.dto.PeriodPatternAnalysisResponse;
import com.example.stockanalysis.dto.StockCandleResponse;
import com.example.stockanalysis.repository.ChartPatternAnalysisRunRepository;
import com.example.stockanalysis.repository.StockCandleRepository;
import com.example.stockanalysis.repository.StockRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Primary
@Profile("postgres")
public class JpaStockPersistencePort implements StockPersistencePort {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final ChartPatternAnalysisRunRepository chartPatternAnalysisRunRepository;

    public JpaStockPersistencePort(
            StockRepository stockRepository,
            StockCandleRepository stockCandleRepository,
            ChartPatternAnalysisRunRepository chartPatternAnalysisRunRepository
    ) {
        this.stockRepository = stockRepository;
        this.stockCandleRepository = stockCandleRepository;
        this.chartPatternAnalysisRunRepository = chartPatternAnalysisRunRepository;
    }

    @Override
    @Transactional
    public void saveStockMaster(Collection<StockDefinition> stocks) {
        stocks.forEach(item -> {
            Stock stock = stockRepository.findBySymbol(item.symbol())
                    .orElseGet(() -> new Stock(
                            item.symbol(),
                            item.externalSymbol(),
                            item.name(),
                            item.market(),
                            item.sector(),
                            item.industry(),
                            null,
                            null,
                            item.description()
                    ));

            stock.setExternalSymbol(item.externalSymbol());
            stock.setName(item.name());
            stock.setMarket(item.market());
            stock.setSector(item.sector());
            stock.setIndustry(item.industry());
            stock.setDescription(item.description());
            stock.setUpdatedAt(java.time.LocalDateTime.now());
            stockRepository.save(stock);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockDefinition> findStocks() {
        return stockRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDefinition)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockDefinition> searchStocks(String keyword, int limit) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return findStocks().stream()
                    .limit(Math.max(0, limit))
                    .toList();
        }
        return stockRepository.search(normalizedKeyword, PageRequest.of(0, Math.max(1, limit))).stream()
                .map(this::toDefinition)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockDefinition> findStock(String symbol) {
        return stockRepository.findBySymbol(symbol)
                .map(this::toDefinition);
    }

    @Override
    @Transactional
    public void saveCandles(String symbol, List<StockCandleResponse> candles, String source, boolean adjusted) {
        candles.forEach(item -> {
            StockCandle candle = stockCandleRepository
                    .findBySymbolAndDateAndSource(symbol, item.timestamp().toLocalDate(), source)
                    .orElseGet(() -> new StockCandle(
                            symbol,
                            item.timestamp().toLocalDate(),
                            item.openPrice(),
                            item.highPrice(),
                            item.lowPrice(),
                            item.closePrice(),
                            item.volume(),
                            adjusted,
                            source
                    ));

            candle.setOpenPrice(item.openPrice());
            candle.setHighPrice(item.highPrice());
            candle.setLowPrice(item.lowPrice());
            candle.setClosePrice(item.closePrice());
            candle.setVolume(item.volume());
            candle.setAdjusted(adjusted);
            candle.touch();
            stockCandleRepository.save(candle);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockCandleResponse> findCandles(String symbol, LocalDate startDate, LocalDate endDate, String source) {
        return stockCandleRepository
                .findBySymbolAndSourceAndDateBetweenOrderByDateAsc(symbol, source, startDate, endDate)
                .stream()
                .map(this::toCandleResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countCandles(String symbol, String source) {
        return stockCandleRepository.countBySymbolAndSource(symbol, source);
    }

    @Override
    @Transactional
    public void saveChartPatternAnalysis(ChartPatternAnalysisResponse response) {
        ChartPatternAnalysisRun run = new ChartPatternAnalysisRun(
                response.symbol(),
                response.name(),
                java.time.LocalDate.now(),
                response.analysisMode(),
                response.patternId(),
                response.rawPattern(),
                response.patternName(),
                response.patternCategory(),
                response.confidence(),
                response.summary(),
                response.patternDescription(),
                response.disclaimer()
        );

        response.periodAnalyses().forEach(period -> run.addPeriodResult(periodResult(period)));
        chartPatternAnalysisRunRepository.save(run);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChartPatternAnalysisResponse> findChartPatternAnalysis(String symbol, LocalDate targetDate) {
        return chartPatternAnalysisRunRepository.findFirstBySymbolAndTargetDateOrderByCreatedAtDesc(symbol, targetDate)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChartPatternAnalysisResponse> findLatestChartPatternAnalysis(String symbol) {
        return chartPatternAnalysisRunRepository.findFirstBySymbolOrderByCreatedAtDesc(symbol)
                .map(this::toResponse);
    }

    private ChartPatternPeriodResult periodResult(PeriodPatternAnalysisResponse period) {
        PatternBacktestMetricResponse reference = period.referenceReturns().stream()
                .findFirst()
                .orElse(null);

        return new ChartPatternPeriodResult(
                period.period(),
                period.patternId(),
                period.rawPattern(),
                period.patternName(),
                period.patternCategory(),
                period.confidence(),
                period.patternDescription(),
                period.imageGenerated(),
                reference == null ? null : reference.averageReturn(),
                reference == null ? null : reference.medianReturn(),
                reference == null ? null : reference.positiveRate(),
                reference == null ? null : reference.worstReturn()
        );
    }

    private ChartPatternAnalysisResponse toResponse(ChartPatternAnalysisRun run) {
        List<PeriodPatternAnalysisResponse> periods = run.getPeriodResults().stream()
                .map(this::toPeriodResponse)
                .toList();

        List<PatternBacktestMetricResponse> backtests = periods.stream()
                .filter(item -> "12M".equalsIgnoreCase(item.period()))
                .findFirst()
                .map(PeriodPatternAnalysisResponse::referenceReturns)
                .orElse(List.of());

        return new ChartPatternAnalysisResponse(
                run.getSymbol(),
                run.getName(),
                run.getPatternId(),
                run.getRawPattern(),
                run.getAnalysisMode(),
                run.getPatternName(),
                run.getPatternCategory(),
                run.getConfidence(),
                run.getSummary(),
                run.getPatternDescription(),
                backtests,
                periods,
                List.of(),
                List.of("DB에 저장된 최신 차트 패턴 분석 결과입니다."),
                run.getDisclaimer()
        );
    }

    private PeriodPatternAnalysisResponse toPeriodResponse(ChartPatternPeriodResult result) {
        List<PatternBacktestMetricResponse> referenceReturns = result.getReferenceAverageReturn() == null
                ? List.of()
                : List.of(new PatternBacktestMetricResponse(
                        result.getPeriod(),
                        result.getReferenceAverageReturn(),
                        result.getReferenceMedianReturn(),
                        result.getReferencePositiveRate(),
                        result.getReferenceWorstReturn()
                ));

        return new PeriodPatternAnalysisResponse(
                result.getPeriod(),
                result.getPatternId(),
                result.getRawPattern(),
                result.getPatternName(),
                result.getPatternCategory(),
                result.getConfidence(),
                result.getPatternDescription(),
                referenceReturns,
                result.getImageGenerated()
        );
    }

    private StockCandleResponse toCandleResponse(StockCandle candle) {
        return new StockCandleResponse(
                candle.getDate().atStartOfDay(SEOUL).toOffsetDateTime(),
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice(),
                candle.getVolume()
        );
    }

    private StockDefinition toDefinition(Stock stock) {
        return new StockDefinition(
                stock.getSymbol(),
                stock.getExternalSymbol(),
                stock.getName(),
                stock.getMarket(),
                stock.getSector(),
                stock.getIndustry(),
                stock.getCurrentPrice(),
                stock.getChangeRate(),
                stock.getDescription()
        );
    }
}
