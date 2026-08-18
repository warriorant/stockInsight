package com.example.stockanalysis.market;

import com.example.stockanalysis.service.StockDefinition;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KrxKindStockMasterClient implements StockMasterClient {

    private static final Logger log = LoggerFactory.getLogger(KrxKindStockMasterClient.class);
    private static final Charset EUC_KR = Charset.forName("EUC-KR");
    private static final Pattern ROW_PATTERN = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CELL_PATTERN = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final HttpClient httpClient;
    private final String stockListUrl;
    private volatile Map<String, StockDefinition> cachedStocks;

    public KrxKindStockMasterClient(
            @Value("${app.stock-master.krx-kind-url:https://kind.krx.co.kr/corpgeneral/corpList.do?method=download&marketType=stockMkt&searchType=13}") String stockListUrl
    ) {
        this.stockListUrl = stockListUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    @Override
    public Map<String, StockDefinition> getKospiStocks(Map<String, StockDefinition> fallbackStocks) {
        Map<String, StockDefinition> current = cachedStocks;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (cachedStocks == null) {
                cachedStocks = loadKospiStocks(fallbackStocks);
            }
            return cachedStocks;
        }
    }

    private Map<String, StockDefinition> loadKospiStocks(Map<String, StockDefinition> fallbackStocks) {
        Map<String, StockDefinition> loaded = fetchKospiStocks().orElseGet(LinkedHashMap::new);
        if (loaded.isEmpty()) {
            log.warn("KOSPI stock master download failed. Using fallback stock list.");
            return Map.copyOf(fallbackStocks);
        }

        fallbackStocks.forEach(loaded::put);
        log.info("Loaded {} KOSPI stocks from KRX KIND stock master.", loaded.size());
        return Map.copyOf(loaded);
    }

    private Optional<Map<String, StockDefinition>> fetchKospiStocks() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(stockListUrl))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/vnd.ms-excel,text/html,application/xhtml+xml")
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("KRX KIND stock master returned status {}", response.statusCode());
                return Optional.empty();
            }

            String document = decode(response.body());
            Map<String, StockDefinition> stocks = parseStockTable(document);
            return stocks.isEmpty() ? Optional.empty() : Optional.of(stocks);
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("KRX KIND stock master request failed.", error);
            return Optional.empty();
        }
    }

    private String decode(byte[] body) {
        String eucKr = new String(body, EUC_KR);
        if (eucKr.contains("종목코드") || eucKr.contains("회사명")) {
            return eucKr;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private Map<String, StockDefinition> parseStockTable(String document) {
        Map<String, StockDefinition> result = new LinkedHashMap<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(document);
        HeaderIndex header = null;

        while (rowMatcher.find()) {
            List<String> cells = cells(rowMatcher.group(1));
            if (cells.isEmpty()) {
                continue;
            }

            if (header == null) {
                HeaderIndex possibleHeader = HeaderIndex.from(cells);
                if (possibleHeader.isUsable()) {
                    header = possibleHeader;
                }
                continue;
            }

            if (cells.size() <= Math.max(header.nameIndex(), header.codeIndex())) {
                continue;
            }

            String code = normalizeCode(cells.get(header.codeIndex()));
            String name = cells.get(header.nameIndex()).trim();
            if (!code.matches("\\d{6}") || name.isBlank()) {
                continue;
            }

            String industry = cellAt(cells, header.industryIndex()).orElse("분류 준비 중");
            String product = cellAt(cells, header.productIndex()).orElse(industry);
            result.put(code, new StockDefinition(
                    code,
                    code + ".KS",
                    name,
                    "KOSPI",
                    inferSector(industry, product, name),
                    industry.isBlank() ? "분류 준비 중" : industry,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "%s 코스피 상장사입니다. 상세 화면에서 현재가, 차트, 재무 지표를 확인할 수 있습니다.".formatted(name)
            ));
        }

        return result;
    }

    private List<String> cells(String row) {
        List<String> result = new ArrayList<>();
        Matcher cellMatcher = CELL_PATTERN.matcher(row);
        while (cellMatcher.find()) {
            result.add(cleanCell(cellMatcher.group(1)));
        }
        return result;
    }

    private String cleanCell(String value) {
        return value
                .replaceAll("(?is)<br\\s*/?>", " ")
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeCode(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() >= 6) {
            return digits.substring(digits.length() - 6);
        }
        return digits;
    }

    private Optional<String> cellAt(List<String> cells, int index) {
        if (index < 0 || index >= cells.size()) {
            return Optional.empty();
        }
        String value = cells.get(index).trim();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String inferSector(String industry, String product, String name) {
        String text = (industry + " " + product + " " + name).toLowerCase(Locale.ROOT);
        if (containsAny(text, "반도체", "전자", "소프트웨어", "정보", "통신", "인터넷", "컴퓨터")) {
            return "기술";
        }
        if (containsAny(text, "은행", "증권", "보험", "금융", "카드", "캐피탈")) {
            return "금융";
        }
        if (containsAny(text, "자동차", "운수", "항공", "여행", "호텔", "유통", "소매")) {
            return "경기소비재";
        }
        if (containsAny(text, "식품", "음료", "생활", "화장품", "담배")) {
            return "필수소비재";
        }
        if (containsAny(text, "제약", "바이오", "의료", "헬스")) {
            return "헬스케어";
        }
        if (containsAny(text, "화학", "철강", "금속", "비금속", "종이", "목재", "섬유")) {
            return "소재";
        }
        if (containsAny(text, "건설", "기계", "조선", "전기", "가스", "에너지", "운송장비")) {
            return "산업재";
        }
        return "분류 준비 중";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record HeaderIndex(int nameIndex, int codeIndex, int industryIndex, int productIndex) {

        static HeaderIndex from(List<String> cells) {
            return new HeaderIndex(
                    find(cells, "회사명"),
                    find(cells, "종목코드"),
                    find(cells, "업종"),
                    find(cells, "주요제품")
            );
        }

        boolean isUsable() {
            return nameIndex >= 0 && codeIndex >= 0;
        }

        private static int find(List<String> cells, String keyword) {
            for (int index = 0; index < cells.size(); index += 1) {
                if (cells.get(index).contains(keyword)) {
                    return index;
                }
            }
            return -1;
        }
    }
}
