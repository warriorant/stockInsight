package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.AiAnalysisRequest;
import com.example.stockanalysis.dto.AiAnalysisResponse;
import org.springframework.stereotype.Component;

@Component
public class UnavailableAiAnalysisClient implements AiAnalysisClient {

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        return new AiAnalysisResponse(
                "AI 종합 분석 서버가 아직 연결되지 않았습니다.",
                "실제 AI 분석 서버 URL을 설정하면 가격 데이터와 재무 데이터를 전달해 분석 결과를 받을 수 있습니다.",
                "현재 화면에는 mock 분석 문장을 표시하지 않습니다.",
                "AI 서버 응답 스펙이 확정되면 app.ai.analysis-server.url 설정만 연결하면 됩니다."
        );
    }
}
