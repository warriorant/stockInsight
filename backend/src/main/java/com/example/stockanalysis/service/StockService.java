package com.example.stockanalysis.service;

import com.example.stockanalysis.dto.FinancialDataResponse;
import com.example.stockanalysis.dto.PricePointResponse;
import com.example.stockanalysis.dto.StockResponse;
import com.example.stockanalysis.market.FinancialDataClient;
import com.example.stockanalysis.market.StockMasterClient;
import com.example.stockanalysis.market.StockMarketClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Map<String, StockDefinition> stocks;
    private final Map<String, String> aliases = createAliases();
    private final Map<String, FinancialDataResponse> financials = Map.of();
    private final StockMarketClient stockMarketClient;
    private final FinancialDataClient financialDataClient;
    private final StockPersistencePort stockPersistencePort;

    public StockService(
            StockMarketClient stockMarketClient,
            FinancialDataClient financialDataClient,
            StockMasterClient stockMasterClient,
            StockPersistencePort stockPersistencePort
    ) {
        this.stockMarketClient = stockMarketClient;
        this.financialDataClient = financialDataClient;
        this.stockPersistencePort = stockPersistencePort;
        this.stocks = stockMasterClient.getKospiStocks(createFallbackStocks());
        this.stockPersistencePort.saveStockMaster(this.stocks.values());
    }

    public List<StockResponse> getStocks() {
        List<StockDefinition> persistedStocks = stockPersistencePort.findStocks();
        if (!persistedStocks.isEmpty()) {
            return persistedStocks.stream()
                    .map(this::toListStock)
                    .toList();
        }

        return stocks.values().stream()
                .map(this::toListStock)
                .sorted(Comparator.comparing(StockResponse::name))
                .toList();
    }

    public List<StockResponse> searchStocks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getStocks();
        }

        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        List<StockDefinition> persistedStocks = stockPersistencePort.searchStocks(normalizedKeyword, 50);
        if (!persistedStocks.isEmpty()) {
            return persistedStocks.stream()
                    .map(this::toListStock)
                    .toList();
        }

        return stocks.values().stream()
                .filter(stock ->
                        stock.name().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                                || stock.symbol().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                                || stock.externalSymbol().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                                || stock.market().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                                || stock.sector().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                                || stock.industry().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                )
                .map(this::toListStock)
                .sorted(Comparator.comparing(StockResponse::name))
                .limit(50)
                .toList();
    }

    public StockResponse getStock(String symbol) {
        return toLiveStock(getDefinition(symbol));
    }

    public List<PricePointResponse> getPrices(String symbol, String range) {
        StockDefinition stock = getDefinition(symbol);
        daysForRange(range);
        List<PricePointResponse> prices = stockMarketClient.getPrices(stock, range)
                .orElseGet(List::of);
        return mergeCurrentPrice(stock, prices);
    }

    public FinancialDataResponse getFinancials(String symbol) {
        StockDefinition stock = getDefinition(symbol);
        FinancialDataResponse data = financials.getOrDefault(stock.symbol(), emptyFinancials());
        return financialDataClient.getFinancials(stock, data).orElse(data);
    }

    private StockDefinition getDefinition(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String lookupSymbol = aliases.getOrDefault(normalizedSymbol, normalizedSymbol);
        java.util.Optional<StockDefinition> persistedStock = stockPersistencePort.findStock(lookupSymbol);
        if (persistedStock.isPresent()) {
            return persistedStock.get();
        }

        StockDefinition stock = stocks.get(normalizedSymbol);
        if (stock == null) {
            String aliasedSymbol = aliases.get(normalizedSymbol);
            stock = aliasedSymbol == null ? null : stocks.get(aliasedSymbol);
        }
        if (stock == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다: " + symbol);
        }
        return stock;
    }

    private StockResponse toLiveStock(StockDefinition stock) {
        return stockMarketClient.getStock(stock)
                .orElseGet(() -> toFallbackStock(stock));
    }

    private StockResponse toListStock(StockDefinition stock) {
        return new StockResponse(
                stock.symbol(),
                stock.name(),
                stock.market(),
                stock.sector(),
                stock.industry(),
                null,
                null,
                stock.description()
        );
    }

    private StockResponse toFallbackStock(StockDefinition stock) {
        return new StockResponse(
                stock.symbol(),
                stock.name(),
                stock.market(),
                stock.sector(),
                stock.industry(),
                null,
                null,
                stock.description()
        );
    }

    private List<PricePointResponse> mergeCurrentPrice(StockDefinition stock, List<PricePointResponse> prices) {
        if (prices.isEmpty()) {
            return prices;
        }

        StockResponse liveStock = toLiveStock(stock);
        BigDecimal currentPrice = liveStock.currentPrice();
        if (currentPrice == null) {
            return prices;
        }
        LocalDate today = LocalDate.now(SEOUL);
        PricePointResponse latest = prices.get(prices.size() - 1);
        List<PricePointResponse> syncedPrices = new ArrayList<>(prices);

        if (latest.date().isBefore(today)) {
            syncedPrices.add(new PricePointResponse(today, currentPrice, 0L));
            return syncedPrices;
        }

        if (latest.date().isEqual(today) && latest.close().compareTo(currentPrice) != 0) {
            syncedPrices.set(syncedPrices.size() - 1, new PricePointResponse(today, currentPrice, latest.volume()));
        }

        return syncedPrices;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private int daysForRange(String range) {
        return switch (range == null ? "3M" : range.toUpperCase(Locale.ROOT)) {
            case "1M" -> 30;
            case "6M" -> 180;
            case "1Y" -> 365;
            case "3M" -> 90;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 차트 기간입니다: " + range);
        };
    }


    private Map<String, String> createAliases() {
        return Map.of(
                "SAMSUNG", "005930",
                "SKHYNIX", "000660",
                "NAVER", "035420",
                "KAKAO", "035720",
                "HYUNDAI", "005380",
                "LGENERGY", "373220"
        );
    }

    private Map<String, StockDefinition> createFallbackStocks() {
        Map<String, StockDefinition> result = new LinkedHashMap<>();
        addFallbackStock(result, "005930", "삼성전자", "정보기술", "반도체 및 전자제품");
        addFallbackStock(result, "000660", "SK하이닉스", "정보기술", "반도체");
        addFallbackStock(result, "373220", "LG에너지솔루션", "산업재", "이차전지");
        addFallbackStock(result, "005380", "현대차", "경기소비재", "자동차");
        addFallbackStock(result, "000270", "기아", "경기소비재", "자동차");
        addFallbackStock(result, "035420", "NAVER", "커뮤니케이션", "인터넷 서비스");
        addFallbackStock(result, "035720", "카카오", "커뮤니케이션", "인터넷 서비스");
        addFallbackStock(result, "051910", "LG화학", "소재", "화학");
        addFallbackStock(result, "006400", "삼성SDI", "산업재", "이차전지");
        addFallbackStock(result, "068270", "셀트리온", "헬스케어", "바이오의약품");
        addFallbackStock(result, "012330", "현대모비스", "경기소비재", "자동차 부품");
        addFallbackStock(result, "105560", "KB금융", "금융", "금융지주");
        addFallbackStock(result, "055550", "신한지주", "금융", "금융지주");
        addFallbackStock(result, "028260", "삼성물산", "산업재", "복합기업");
        addFallbackStock(result, "096770", "SK이노베이션", "에너지", "정유 및 배터리");
        addFallbackStock(result, "032830", "삼성생명", "금융", "생명보험");
        addFallbackStock(result, "066570", "LG전자", "경기소비재", "전자제품");
        addFallbackStock(result, "003550", "LG", "산업재", "지주회사");
        addFallbackStock(result, "015760", "한국전력", "유틸리티", "전력");
        addFallbackStock(result, "086790", "하나금융지주", "금융", "금융지주");
        return Map.copyOf(result);
    }

    private void addFallbackStock(
            Map<String, StockDefinition> result,
            String symbol,
            String name,
            String sector,
            String industry
    ) {
        result.put(symbol, new StockDefinition(
                symbol,
                symbol + ".KS",
                name,
                "KOSPI",
                sector,
                industry,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "%s 코스피 상장사입니다. KRX 종목 목록을 불러오지 못할 때도 발표용 핵심 기능을 확인할 수 있도록 기본 목록에 포함되어 있습니다.".formatted(name)
        ));
    }

    private FinancialDataResponse emptyFinancials() {
        return new FinancialDataResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
