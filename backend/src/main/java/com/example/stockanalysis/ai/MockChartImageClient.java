package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.ChartPatternAnalysisRequest;
import com.example.stockanalysis.dto.PricePointResponse;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.chart-image.mock-enabled", havingValue = "true")
public class MockChartImageClient implements ChartImageClient {

    private static final Logger log = LoggerFactory.getLogger(MockChartImageClient.class);

    @Override
    public List<ChartImageResponse> createChartImages(ChartPatternAnalysisRequest request) {
        return request.priceDataByPeriod()
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt(entry -> periodOrder(entry.getKey())))
                .map(entry -> renderChart(request.symbol(), entry.getKey(), entry.getValue()))
                .toList();
    }

    private int periodOrder(String period) {
        if ("6M".equalsIgnoreCase(period)) {
            return 0;
        }
        if ("12M".equalsIgnoreCase(period) || "1Y".equalsIgnoreCase(period)) {
            return 1;
        }
        return 2;
    }

    private ChartImageResponse renderChart(String symbol, String period, List<PricePointResponse> prices) {
        try {
            BufferedImage image = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 320, 320);
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            if (prices.size() > 1) {
                BigDecimal min = prices.stream()
                        .map(PricePointResponse::close)
                        .min(Comparator.naturalOrder())
                        .orElse(BigDecimal.ONE);
                BigDecimal max = prices.stream()
                        .map(PricePointResponse::close)
                        .max(Comparator.naturalOrder())
                        .orElse(BigDecimal.TEN);
                BigDecimal range = max.subtract(min);
                if (range.compareTo(BigDecimal.ZERO) == 0) {
                    range = BigDecimal.ONE;
                }

                int previousX = 18;
                int previousY = scaleY(prices.get(0).close(), min, range);
                for (int index = 1; index < prices.size(); index++) {
                    int x = 18 + (int) Math.round(index * 284.0 / (prices.size() - 1));
                    int y = scaleY(prices.get(index).close(), min, range);
                    graphics.drawLine(previousX, previousY, x, y);
                    previousX = x;
                    previousY = y;
                }
            }

            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return new ChartImageResponse(
                    period,
                    output.toByteArray(),
                    "%s-%s-chart.png".formatted(symbol, period),
                    "image/png"
            );
        } catch (Exception error) {
            log.warn("Mock chart image render failed. symbol={}, period={}", symbol, period, error);
            return new ChartImageResponse(period, new byte[0], "%s-%s-chart.png".formatted(symbol, period), "image/png");
        }
    }

    private int scaleY(BigDecimal value, BigDecimal min, BigDecimal range) {
        BigDecimal normalized = value.subtract(min).divide(range, 6, java.math.RoundingMode.HALF_UP);
        return 294 - (int) Math.round(normalized.doubleValue() * 268);
    }
}
