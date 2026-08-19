package com.example.stockanalysis.service;

import com.example.stockanalysis.dto.ChartPatternAnalysisResponse;
import com.example.stockanalysis.dto.PatternBacktestMetricResponse;
import com.example.stockanalysis.dto.PeriodPatternAnalysisResponse;
import com.example.stockanalysis.service.ChartPatternCatalogService.ChartPatternDefinition;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("postgres")
public class DemoChartPatternSeedService {

    private static final Logger log = LoggerFactory.getLogger(DemoChartPatternSeedService.class);
    private static final String ANALYSIS_MODE = "demo-db-seed";

    private final StockPersistencePort stockPersistencePort;
    private final ChartPatternCatalogService chartPatternCatalogService;
    private final boolean seedEnabled;
    private final boolean resetEnabled;

    public DemoChartPatternSeedService(
            StockPersistencePort stockPersistencePort,
            ChartPatternCatalogService chartPatternCatalogService,
            @Value("${app.ai.chart-pattern.demo-seed-enabled:true}") boolean seedEnabled,
            @Value("${app.ai.chart-pattern.demo-seed-reset:true}") boolean resetEnabled
    ) {
        this.stockPersistencePort = stockPersistencePort;
        this.chartPatternCatalogService = chartPatternCatalogService;
        this.seedEnabled = seedEnabled;
        this.resetEnabled = resetEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoAnalysis() {
        if (!seedEnabled) {
            return;
        }

        if (resetEnabled) {
            stockPersistencePort.deleteAllChartPatternAnalyses();
        }

        int inserted = 0;
        for (DemoStock stock : demoStocks()) {
            if (!resetEnabled && hasDemoSeed(stock.symbol())) {
                continue;
            }
            stockPersistencePort.saveChartPatternAnalysis(response(stock));
            inserted++;
        }

        if (inserted > 0) {
            log.info("Demo chart pattern seed inserted. count={}", inserted);
        } else {
            log.info("Demo chart pattern seed already exists.");
        }
    }

    private boolean hasDemoSeed(String symbol) {
        return stockPersistencePort.findLatestChartPatternAnalysis(symbol)
                .map(ChartPatternAnalysisResponse::analysisMode)
                .filter(ANALYSIS_MODE::equals)
                .isPresent();
    }

    private ChartPatternAnalysisResponse response(DemoStock stock) {
        ChartPatternDefinition definition = chartPatternCatalogService.getDefinition(stock.patternId());
        List<PeriodPatternAnalysisResponse> periods = List.of(
                period(stock, definition, "6M", confidence(stock.confidence(), -0.04)),
                period(stock, definition, "12M", stock.confidence())
        );

        return new ChartPatternAnalysisResponse(
                stock.symbol(),
                stock.name(),
                definition.patternId(),
                "패턴%d".formatted(definition.patternId()),
                ANALYSIS_MODE,
                definition.name(),
                definition.category(),
                stock.confidence(),
                "%s의 차트는 발표용 사전 분석 DB에서 %s 패턴으로 분류되어 있습니다. 이 결과는 AI 서버 실시간 호출 없이 저장된 결과를 조회한 것입니다."
                        .formatted(stock.name(), definition.name()),
                definition.description(),
                definition.referenceReturns(),
                periods,
                List.of(),
                List.of(
                        "발표용 DB에 사전 저장된 분석 결과",
                        "실제 서비스 전환 시 AI 서버 배치 결과로 교체 가능",
                        "패턴별 참고 수익률은 투자 지시가 아닌 비교 지표"
                ),
                "이 분석은 발표용 사전 저장 결과이며, 특정 투자 행동을 지시하지 않습니다."
        );
    }

    private PeriodPatternAnalysisResponse period(
            DemoStock stock,
            ChartPatternDefinition definition,
            String period,
            BigDecimal confidence
    ) {
        PatternBacktestMetricResponse referenceReturn = chartPatternCatalogService.referenceReturn(
                definition.patternId(),
                period
        );

        return new PeriodPatternAnalysisResponse(
                period,
                definition.patternId(),
                "패턴%d".formatted(definition.patternId()),
                definition.name(),
                definition.category(),
                confidence,
                definition.description(),
                List.of(referenceReturn),
                true,
                chartImageDataUrl(definition.patternId(), period, stock.name())
        );
    }

    private BigDecimal confidence(BigDecimal base, double adjustment) {
        BigDecimal adjusted = base.add(BigDecimal.valueOf(adjustment));
        if (adjusted.compareTo(BigDecimal.valueOf(0.55)) < 0) {
            return BigDecimal.valueOf(0.55);
        }
        if (adjusted.compareTo(BigDecimal.valueOf(0.94)) > 0) {
            return BigDecimal.valueOf(0.94);
        }
        return adjusted.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String chartImageDataUrl(int patternId, String period, String stockName) {
        String flow = flowPath(patternId);
        String guide = guidePath(patternId);
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 360">
                  <rect width="640" height="360" fill="#ffffff"/>
                  <g stroke="#dde3ea" stroke-width="1">
                    <path d="M48 58H600"/><path d="M48 126H600"/><path d="M48 194H600"/><path d="M48 262H600"/>
                  </g>
                  <path d="%s" fill="none" stroke="#f0b90b" stroke-width="4" stroke-dasharray="10 10" stroke-linecap="round"/>
                  <path d="%s" fill="none" stroke="#0ecb81" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
                  <circle cx="590" cy="%d" r="7" fill="#f0b90b" stroke="#111827" stroke-width="3"/>
                  <text x="48" y="326" fill="#111827" font-family="Arial, sans-serif" font-size="18" font-weight="700">%s %s</text>
                </svg>
                """.formatted(guide, flow, endY(patternId), escape(stockName), period);
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private String flowPath(int patternId) {
        return switch (patternId) {
            case 0 -> "M50 168 C120 126 160 230 230 188 C300 146 340 228 410 184 C480 142 520 226 590 180";
            case 1 -> "M50 250 L120 218 L190 205 L260 168 L330 152 L400 112 L470 96 L590 58";
            case 2 -> "M50 80 L120 118 L190 104 L260 150 L330 140 L400 190 L470 178 L590 238";
            case 3 -> "M50 72 L130 242 L210 98 L290 218 L370 128 L450 194 L590 160";
            case 4 -> "M50 240 L130 206 L210 210 L290 166 L370 170 L450 118 L590 120";
            case 5 -> "M50 94 L130 140 L210 134 L290 178 L370 174 L450 218 L590 214";
            case 6 -> "M50 92 L120 142 L190 126 L260 176 L330 164 L400 206 L470 196 L590 184";
            case 7 -> "M50 230 L120 180 L190 194 L260 144 L330 154 L400 114 L470 124 L590 136";
            case 8 -> "M50 172 L110 104 L180 236 L250 88 L320 262 L390 72 L470 286 L590 132";
            case 9 -> "M50 232 C100 160 145 152 180 210 C230 54 292 54 335 214 C385 158 435 158 475 230 L590 254";
            case 10 -> "M50 104 C100 176 145 184 180 126 C230 282 292 282 335 122 C385 178 435 178 475 106 L590 82";
            case 11 -> "M50 230 C110 110 160 72 220 110 C270 150 310 220 350 198 C405 84 470 80 520 140 C552 182 570 218 590 244";
            case 12 -> "M50 100 C110 220 165 266 220 216 C270 170 310 96 350 120 C410 254 485 250 530 168 C555 124 570 100 590 84";
            case 13 -> "M50 238 C96 118 145 76 194 126 C238 172 270 208 312 134 C358 76 410 88 454 140 C494 98 545 106 590 226";
            case 14 -> "M50 92 C96 212 145 254 194 204 C238 158 270 122 312 196 C358 254 410 242 454 190 C494 232 545 224 590 104";
            case 15 -> "M50 118 C135 262 284 282 386 116 C420 74 480 114 454 158 C500 138 540 116 590 76";
            case 16 -> "M50 234 C130 254 210 250 285 214 C365 176 430 120 500 88 C538 72 566 62 590 58";
            case 17 -> "M50 250 L170 84 L255 120 L340 132 L425 146 L590 82";
            case 18 -> "M50 72 L170 238 L255 204 L340 190 L425 174 L590 238";
            default -> "M50 190 C115 130 160 246 230 174 C300 102 348 220 420 146 C492 74 530 130 590 96";
        };
    }

    private String guidePath(int patternId) {
        return switch (patternId) {
            case 1, 4, 6, 10, 12, 14, 15, 16, 17 -> "M50 268L590 70";
            case 2, 5, 7, 9, 11, 13, 18 -> "M50 80L590 250";
            case 0 -> "M50 105H590";
            default -> "M50 270L590 70";
        };
    }

    private int endY(int patternId) {
        return switch (patternId) {
            case 1, 10, 12, 15, 16, 17 -> 82;
            case 2, 5, 9, 11, 13, 18 -> 238;
            case 0, 3, 4, 6, 7, 8, 14 -> 160;
            default -> 96;
        };
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private List<DemoStock> demoStocks() {
        return List.of(
                stock("005930", "삼성전자", 1, "0.88"),
                stock("000660", "SK하이닉스", 17, "0.86"),
                stock("373220", "LG에너지솔루션", 4, "0.82"),
                stock("005380", "현대차", 12, "0.84"),
                stock("000270", "기아", 15, "0.87"),
                stock("035420", "NAVER", 10, "0.80"),
                stock("035720", "카카오", 8, "0.73"),
                stock("051910", "LG화학", 6, "0.81"),
                stock("006400", "삼성SDI", 5, "0.76"),
                stock("068270", "셀트리온", 16, "0.83"),
                stock("012330", "현대모비스", 3, "0.75"),
                stock("105560", "KB금융", 0, "0.79"),
                stock("055550", "신한지주", 2, "0.74"),
                stock("028260", "삼성물산", 11, "0.78"),
                stock("096770", "SK이노베이션", 18, "0.77"),
                stock("032830", "삼성생명", 7, "0.72"),
                stock("066570", "LG전자", 14, "0.82"),
                stock("003550", "LG", 13, "0.76"),
                stock("015760", "한국전력", 9, "0.74"),
                stock("086790", "하나금융지주", 19, "0.66")
        );
    }

    private DemoStock stock(String symbol, String name, int patternId, String confidence) {
        return new DemoStock(symbol, name, patternId, new BigDecimal(confidence));
    }

    private record DemoStock(String symbol, String name, int patternId, BigDecimal confidence) {
    }
}
