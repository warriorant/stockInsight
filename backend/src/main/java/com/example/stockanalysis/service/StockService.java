package com.example.stockanalysis.service;

import com.example.stockanalysis.dto.FinancialDataResponse;
import com.example.stockanalysis.dto.PricePointResponse;
import com.example.stockanalysis.dto.StockResponse;
import com.example.stockanalysis.market.StockMarketClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Map<String, StockDefinition> stocks = createStocks();
    private final Map<String, FinancialDataResponse> financials = createFinancials();
    private final StockMarketClient stockMarketClient;

    public StockService(StockMarketClient stockMarketClient) {
        this.stockMarketClient = stockMarketClient;
    }

    public List<StockResponse> getStocks() {
        return stocks.values().stream()
                .map(this::toLiveStock)
                .sorted(Comparator.comparing(StockResponse::name))
                .toList();
    }

    public List<StockResponse> searchStocks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getStocks();
        }

        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);

        return stocks.values().stream()
                .filter(stock ->
                        stock.name().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                                || stock.symbol().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                                || stock.externalSymbol().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                                || stock.market().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                                || stock.sector().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                                || stock.industry().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                )
                .map(this::toLiveStock)
                .sorted(Comparator.comparing(StockResponse::name))
                .toList();
    }

    public StockResponse getStock(String symbol) {
        return toLiveStock(getDefinition(symbol));
    }

    public List<PricePointResponse> getPrices(String symbol, String range) {
        StockDefinition stock = getDefinition(symbol);
        daysForRange(range);
        List<PricePointResponse> prices = stockMarketClient.getPrices(stock, range)
                .orElseGet(() -> createMockPrices(stock, range));
        return mergeCurrentPrice(stock, prices);
    }

    public FinancialDataResponse getFinancials(String symbol) {
        FinancialDataResponse data = financials.get(normalizeSymbol(symbol));
        if (data == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "재무 데이터가 없습니다: " + symbol);
        }
        return data;
    }

    private StockDefinition getDefinition(String symbol) {
        StockDefinition stock = stocks.get(normalizeSymbol(symbol));
        if (stock == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다: " + symbol);
        }
        return stock;
    }

    private StockResponse toLiveStock(StockDefinition stock) {
        return stockMarketClient.getStock(stock)
                .orElseGet(() -> toFallbackStock(stock));
    }

    private StockResponse toFallbackStock(StockDefinition stock) {
        return new StockResponse(
                stock.symbol(),
                stock.name(),
                stock.market(),
                stock.sector(),
                stock.industry(),
                stock.fallbackPrice(),
                stock.fallbackChangeRate(),
                stock.description()
        );
    }

    private List<PricePointResponse> mergeCurrentPrice(StockDefinition stock, List<PricePointResponse> prices) {
        if (prices.isEmpty()) {
            return prices;
        }

        StockResponse liveStock = toLiveStock(stock);
        BigDecimal currentPrice = liveStock.currentPrice();
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

    private List<PricePointResponse> createMockPrices(StockDefinition stock, String range) {
        int days = daysForRange(range);
        int seed = Math.abs(stock.symbol().hashCode() % 17) + 3;
        BigDecimal basePrice = stock.fallbackPrice().multiply(BigDecimal.valueOf(0.92));

        return IntStream.rangeClosed(0, days)
                .mapToObj(index -> {
                    BigDecimal wave = BigDecimal.valueOf(Math.sin((index + seed) / 5.0) * 0.028 + index * 0.0017);
                    BigDecimal close = basePrice.multiply(BigDecimal.ONE.add(wave))
                            .setScale(0, RoundingMode.HALF_UP);
                    long volume = 900_000L + ((long) index * 27_000L) + (seed * 11_000L);
                    return new PricePointResponse(LocalDate.now().minusDays(days - index), close, volume);
                })
                .toList();
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

    private Map<String, StockDefinition> createStocks() {
        Map<String, StockDefinition> data = new LinkedHashMap<>();
        data.put("SAMSUNG", new StockDefinition(
                "SAMSUNG",
                "005930.KS",
                "삼성전자",
                "KOSPI",
                "기술",
                "반도체",
                BigDecimal.valueOf(78100),
                BigDecimal.valueOf(1.85),
                "메모리 반도체, 모바일, 디스플레이 사업을 보유한 국내 대표 기술 기업입니다."
        ));
        data.put("SKHYNIX", new StockDefinition(
                "SKHYNIX",
                "000660.KS",
                "SK하이닉스",
                "KOSPI",
                "기술",
                "메모리 반도체",
                BigDecimal.valueOf(204500),
                BigDecimal.valueOf(2.42),
                "DRAM, NAND, HBM 수요에 민감한 글로벌 메모리 반도체 기업입니다."
        ));
        data.put("NAVER", new StockDefinition(
                "NAVER",
                "035420.KS",
                "네이버",
                "KOSPI",
                "커뮤니케이션",
                "인터넷 플랫폼",
                BigDecimal.valueOf(184200),
                BigDecimal.valueOf(-0.74),
                "검색, 커머스, 콘텐츠, 클라우드 서비스를 운영하는 인터넷 플랫폼 기업입니다."
        ));
        data.put("KAKAO", new StockDefinition(
                "KAKAO",
                "035720.KS",
                "카카오",
                "KOSPI",
                "커뮤니케이션",
                "모바일 플랫폼",
                BigDecimal.valueOf(51200),
                BigDecimal.valueOf(-1.21),
                "메신저 기반 플랫폼에서 결제, 콘텐츠, 모빌리티 사업으로 확장하는 기업입니다."
        ));
        data.put("HYUNDAI", new StockDefinition(
                "HYUNDAI",
                "005380.KS",
                "현대차",
                "KOSPI",
                "경기소비재",
                "자동차",
                BigDecimal.valueOf(247000),
                BigDecimal.valueOf(0.56),
                "전기차, 하이브리드, 글로벌 완성차 판매를 중심으로 성장하는 자동차 기업입니다."
        ));
        data.put("LGENERGY", new StockDefinition(
                "LGENERGY",
                "373220.KS",
                "LG에너지솔루션",
                "KOSPI",
                "산업재",
                "배터리",
                BigDecimal.valueOf(384000),
                BigDecimal.valueOf(1.12),
                "전기차 배터리와 에너지 저장장치 사업을 운영하는 배터리 기업입니다."
        ));
        return data;
    }

    private Map<String, FinancialDataResponse> createFinancials() {
        Map<String, FinancialDataResponse> data = new LinkedHashMap<>();
        data.put("SAMSUNG", new FinancialDataResponse(
                BigDecimal.valueOf(466_000_000_000_000L),
                BigDecimal.valueOf(18.2),
                BigDecimal.valueOf(1.45),
                BigDecimal.valueOf(13.1),
                BigDecimal.valueOf(4280),
                BigDecimal.valueOf(11.6),
                BigDecimal.valueOf(2.1),
                BigDecimal.valueOf(35.4)
        ));
        data.put("SKHYNIX", new FinancialDataResponse(
                BigDecimal.valueOf(149_000_000_000_000L),
                BigDecimal.valueOf(25.7),
                BigDecimal.valueOf(2.02),
                BigDecimal.valueOf(15.8),
                BigDecimal.valueOf(7960),
                BigDecimal.valueOf(24.3),
                BigDecimal.valueOf(0.8),
                BigDecimal.valueOf(63.2)
        ));
        data.put("NAVER", new FinancialDataResponse(
                BigDecimal.valueOf(30_000_000_000_000L),
                BigDecimal.valueOf(21.6),
                BigDecimal.valueOf(1.18),
                BigDecimal.valueOf(9.7),
                BigDecimal.valueOf(8520),
                BigDecimal.valueOf(8.1),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(42.6)
        ));
        data.put("KAKAO", new FinancialDataResponse(
                BigDecimal.valueOf(22_000_000_000_000L),
                BigDecimal.valueOf(34.8),
                BigDecimal.valueOf(1.72),
                BigDecimal.valueOf(6.2),
                BigDecimal.valueOf(1470),
                BigDecimal.valueOf(5.4),
                BigDecimal.valueOf(0.0),
                BigDecimal.valueOf(78.5)
        ));
        data.put("HYUNDAI", new FinancialDataResponse(
                BigDecimal.valueOf(52_000_000_000_000L),
                BigDecimal.valueOf(6.4),
                BigDecimal.valueOf(0.68),
                BigDecimal.valueOf(14.4),
                BigDecimal.valueOf(38500),
                BigDecimal.valueOf(9.2),
                BigDecimal.valueOf(4.1),
                BigDecimal.valueOf(91.8)
        ));
        data.put("LGENERGY", new FinancialDataResponse(
                BigDecimal.valueOf(89_000_000_000_000L),
                BigDecimal.valueOf(48.3),
                BigDecimal.valueOf(4.15),
                BigDecimal.valueOf(7.8),
                BigDecimal.valueOf(7950),
                BigDecimal.valueOf(14.5),
                BigDecimal.valueOf(0.0),
                BigDecimal.valueOf(112.1)
        ));
        return data;
    }
}
