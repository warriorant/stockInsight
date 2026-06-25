package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.AiAnalysisRequest;
import com.example.stockanalysis.dto.AiAnalysisResponse;
import com.example.stockanalysis.dto.PricePointResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class MockAiAnalysisClient implements AiAnalysisClient {

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        BigDecimal trendRate = calculateTrendRate(request);
        int score = calculateScore(request, trendRate);
        String rating = ratingFor(score);
        String trendText = trendRate.compareTo(BigDecimal.ZERO) >= 0 ? "상승" : "하락";

        return new AiAnalysisResponse(
                "%s는 최근 가격 흐름과 재무 지표를 함께 보면 %s 관점입니다.".formatted(request.name(), rating),
                "선택한 기간의 가격은 약 %s%% %s했습니다. 단기 변동성은 있지만 추세 확인에는 추가 거래량 데이터가 필요합니다."
                        .formatted(trendRate.abs().setScale(1, RoundingMode.HALF_UP), trendText),
                "ROE와 매출 성장률은 양호한 편이며, PER과 PBR은 동종 업계 비교가 필요합니다.",
                "금리, 환율, 업황 변화에 따라 실적 추정치가 흔들릴 수 있습니다. mock 데이터 기반 결과이므로 투자 판단에는 실제 데이터 검증이 필요합니다.",
                score,
                rating
        );
    }

    private BigDecimal calculateTrendRate(AiAnalysisRequest request) {
        if (request.priceData().size() < 2) {
            return BigDecimal.ZERO;
        }

        PricePointResponse first = request.priceData().get(0);
        PricePointResponse last = request.priceData().get(request.priceData().size() - 1);

        if (first.close().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return last.close()
                .subtract(first.close())
                .divide(first.close(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private int calculateScore(AiAnalysisRequest request, BigDecimal trendRate) {
        int score = 68;

        if (trendRate.compareTo(BigDecimal.ZERO) > 0) {
            score += 8;
        }
        if (request.financialData().roe().compareTo(BigDecimal.valueOf(12)) >= 0) {
            score += 8;
        }
        if (request.financialData().debtRatio().compareTo(BigDecimal.valueOf(100)) <= 0) {
            score += 6;
        }
        if (request.financialData().per().compareTo(BigDecimal.valueOf(30)) > 0) {
            score -= 7;
        }

        return Math.max(35, Math.min(score, 95));
    }

    private String ratingFor(int score) {
        if (score >= 82) {
            return "BUY";
        }
        if (score >= 65) {
            return "HOLD";
        }
        return "CAUTION";
    }
}

