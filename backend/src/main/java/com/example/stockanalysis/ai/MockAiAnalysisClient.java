package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.AiAnalysisRequest;
import com.example.stockanalysis.dto.AiAnalysisResponse;
import com.example.stockanalysis.dto.PricePointResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.analysis-server.mock-enabled", havingValue = "true")
public class MockAiAnalysisClient implements AiAnalysisClient {

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        BigDecimal trendRate = calculateTrendRate(request);
        String trendText = trendRate.compareTo(BigDecimal.ZERO) >= 0 ? "상승" : "하락";

        return new AiAnalysisResponse(
                "%s의 가격 흐름, 재무 지표, 시장 일정을 함께 정리한 참고 분석이며, 특정 투자 행동을 지시하지 않습니다.".formatted(request.name()),
                "선택한 기간의 가격은 약 %s%% %s했습니다. 이 값은 방향성을 단정하기보다 차트 패턴을 해석하기 위한 참고값입니다."
                        .formatted(trendRate.abs().setScale(1, RoundingMode.HALF_UP), trendText),
                "PER, PBR, ROE는 단독 판단 지표가 아니라 업종 평균과 회사의 성장률을 함께 비교해야 합니다.",
                "금리, 환율, 업황 변화에 따라 가격 흐름이 달라질 수 있습니다. mock 데이터 기반 결과이므로 실제 판단에는 추가 검증이 필요합니다."
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

}
