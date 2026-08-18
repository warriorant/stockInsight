package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.FinancialDataResponse;
import com.example.stockanalysis.service.StockDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Component
public class OpenDartFinancialDataClient implements FinancialDataClient {

    private static final Logger log = LoggerFactory.getLogger(OpenDartFinancialDataClient.class);
    private static final String CORP_CODE_URL = "https://opendart.fss.or.kr/api/corpCode.xml";
    private static final String FINANCIAL_STATEMENT_URL = "https://opendart.fss.or.kr/api/fnlttSinglAcnt.json";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private volatile Map<String, String> corpCodeByStockCode;

    public OpenDartFinancialDataClient(
            ObjectMapper objectMapper,
            @Value("${app.financials.opendart.api-key:}") String apiKey
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public Optional<FinancialDataResponse> getFinancials(StockDefinition stock, FinancialDataResponse fallback) {
        if (apiKey.isBlank()) {
            return Optional.empty();
        }

        String stockCode = stockCode(stock);
        if (stockCode.isBlank()) {
            return Optional.empty();
        }

        Optional<String> corpCode = corpCodeFor(stockCode);
        if (corpCode.isEmpty()) {
            return Optional.empty();
        }

        return fetchFinancialStatement(corpCode.get(), businessYear())
                .map(statement -> mergeWithFallback(fallback, statement));
    }

    private Optional<String> corpCodeFor(String stockCode) {
        Map<String, String> currentMap = corpCodeByStockCode;
        if (currentMap == null) {
            synchronized (this) {
                if (corpCodeByStockCode == null) {
                    corpCodeByStockCode = loadCorpCodes();
                }
                currentMap = corpCodeByStockCode;
            }
        }
        return Optional.ofNullable(currentMap.get(stockCode));
    }

    private Map<String, String> loadCorpCodes() {
        URI uri = UriComponentsBuilder.fromUriString(CORP_CODE_URL)
                .queryParam("crtfc_key", apiKey)
                .build()
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/zip, application/octet-stream")
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenDART corpCode returned status {}", response.statusCode());
                return Map.of();
            }
            return parseCorpCodeZip(response.body());
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("OpenDART corpCode request failed", error);
            return Map.of();
        }
    }

    private Map<String, String> parseCorpCodeZip(byte[] zipBytes) throws IOException {
        Map<String, String> result = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".xml")) {
                    result.putAll(parseCorpCodeXml(readEntry(zipInputStream)));
                }
                zipInputStream.closeEntry();
            }
        }
        log.info("OpenDART corpCode map loaded. count={}", result.size());
        return result;
    }

    private byte[] readEntry(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        zipInputStream.transferTo(outputStream);
        return outputStream.toByteArray();
    }

    private Map<String, String> parseCorpCodeXml(byte[] xmlBytes) {
        Map<String, String> result = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
            NodeList list = document.getElementsByTagName("list");
            for (int index = 0; index < list.getLength(); index++) {
                Element element = (Element) list.item(index);
                String corpCode = childText(element, "corp_code");
                String stockCode = childText(element, "stock_code");
                if (!corpCode.isBlank() && !stockCode.isBlank()) {
                    result.put(stockCode, corpCode);
                }
            }
        } catch (IOException | ParserConfigurationException | SAXException | RuntimeException error) {
            log.warn("OpenDART corpCode XML parse failed", error);
        }
        return result;
    }

    private Optional<StatementValues> fetchFinancialStatement(String corpCode, int businessYear) {
        URI uri = UriComponentsBuilder.fromUriString(FINANCIAL_STATEMENT_URL)
                .queryParam("crtfc_key", apiKey)
                .queryParam("corp_code", corpCode)
                .queryParam("bsns_year", businessYear)
                .queryParam("reprt_code", "11011")
                .queryParam("fs_div", "CFS")
                .build()
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenDART financial statement returned status {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!"000".equals(root.path("status").asText("")) || !root.path("list").isArray()) {
                log.warn("OpenDART financial statement returned status={}, message={}",
                        root.path("status").asText(""), root.path("message").asText(""));
                return Optional.empty();
            }

            return parseStatementValues(root.path("list"));
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("OpenDART financial statement request failed", error);
            return Optional.empty();
        }
    }

    private Optional<StatementValues> parseStatementValues(JsonNode rows) {
        BigDecimal revenue = null;
        BigDecimal previousRevenue = null;
        BigDecimal netIncome = null;
        BigDecimal equity = null;
        BigDecimal liabilities = null;

        for (JsonNode row : rows) {
            String accountName = text(row, "account_nm");
            Optional<BigDecimal> currentAmount = amount(row, "thstrm_amount");
            Optional<BigDecimal> previousAmount = amount(row, "frmtrm_amount");

            if (currentAmount.isEmpty()) {
                continue;
            }

            if (isRevenue(accountName) && revenue == null) {
                revenue = currentAmount.get();
                previousRevenue = previousAmount.orElse(null);
            } else if (isNetIncome(accountName) && netIncome == null) {
                netIncome = currentAmount.get();
            } else if (accountName.contains("자본총계")) {
                equity = currentAmount.get();
            } else if (accountName.contains("부채총계")) {
                liabilities = currentAmount.get();
            }
        }

        if (revenue == null && netIncome == null && equity == null && liabilities == null) {
            return Optional.empty();
        }
        return Optional.of(new StatementValues(revenue, previousRevenue, netIncome, equity, liabilities));
    }

    private FinancialDataResponse mergeWithFallback(FinancialDataResponse fallback, StatementValues statement) {
        BigDecimal roe = ratioPercent(statement.netIncome, statement.equity).orElse(fallback.roe());
        BigDecimal revenueGrowth = growthPercent(statement.revenue, statement.previousRevenue).orElse(fallback.revenueGrowth());
        BigDecimal debtRatio = ratioPercent(statement.liabilities, statement.equity).orElse(fallback.debtRatio());

        return new FinancialDataResponse(
                fallback.marketCap(),
                fallback.per(),
                fallback.pbr(),
                roe,
                fallback.eps(),
                revenueGrowth,
                fallback.dividendYield(),
                debtRatio
        );
    }

    private Optional<BigDecimal> ratioPercent(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }
        return Optional.of(numerator
                .multiply(BigDecimal.valueOf(100))
                .divide(denominator, 1, RoundingMode.HALF_UP));
    }

    private Optional<BigDecimal> growthPercent(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }
        return Optional.of(current
                .subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 1, RoundingMode.HALF_UP));
    }

    private Optional<BigDecimal> amount(JsonNode row, String fieldName) {
        String raw = text(row, fieldName)
                .replace(",", "")
                .replace(" ", "")
                .replace("−", "-");
        if (raw.isBlank() || raw.equals("-")) {
            return Optional.empty();
        }

        boolean negative = raw.startsWith("(") && raw.endsWith(")");
        String normalized = raw.replace("(", "").replace(")", "");
        try {
            BigDecimal amount = new BigDecimal(normalized);
            return Optional.of(negative ? amount.negate() : amount);
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private String childText(Element element, String tagName) {
        NodeList children = element.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return "";
        }
        return children.item(0).getTextContent().trim();
    }

    private boolean isRevenue(String accountName) {
        return (accountName.contains("매출액") || accountName.contains("영업수익"))
                && !accountName.contains("원가");
    }

    private boolean isNetIncome(String accountName) {
        return accountName.contains("당기순이익") && !accountName.contains("주당");
    }

    private String stockCode(StockDefinition stock) {
        String externalSymbol = stock.externalSymbol() == null ? "" : stock.externalSymbol().trim();
        int dotIndex = externalSymbol.indexOf('.');
        return dotIndex >= 0 ? externalSymbol.substring(0, dotIndex) : externalSymbol;
    }

    private int businessYear() {
        LocalDate today = LocalDate.now(SEOUL);
        return today.getMonthValue() <= 3 ? today.getYear() - 2 : today.getYear() - 1;
    }

    private record StatementValues(
            BigDecimal revenue,
            BigDecimal previousRevenue,
            BigDecimal netIncome,
            BigDecimal equity,
            BigDecimal liabilities
    ) {
    }
}
