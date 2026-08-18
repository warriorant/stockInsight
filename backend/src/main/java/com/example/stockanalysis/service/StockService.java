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
        this.stocks = stockMasterClient.getKospiStocks(Map.of());
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
