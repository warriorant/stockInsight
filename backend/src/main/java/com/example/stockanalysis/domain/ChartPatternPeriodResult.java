package com.example.stockanalysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "chart_pattern_period_results")
public class ChartPatternPeriodResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private ChartPatternAnalysisRun analysisRun;

    @Column(nullable = false, length = 16)
    private String period;

    private Integer patternId;

    @Column(length = 128)
    private String rawPattern;

    @Column(length = 128)
    private String patternName;

    @Column(length = 128)
    private String patternCategory;

    @Column(precision = 8, scale = 6)
    private BigDecimal confidence;

    @Column(length = 2000)
    private String patternDescription;

    private Boolean imageGenerated;

    @Column(precision = 8, scale = 2)
    private BigDecimal referenceAverageReturn;

    @Column(precision = 8, scale = 2)
    private BigDecimal referenceMedianReturn;

    @Column(precision = 8, scale = 2)
    private BigDecimal referencePositiveRate;

    @Column(precision = 8, scale = 2)
    private BigDecimal referenceWorstReturn;

    protected ChartPatternPeriodResult() {
    }

    public ChartPatternPeriodResult(
            String period,
            Integer patternId,
            String rawPattern,
            String patternName,
            String patternCategory,
            BigDecimal confidence,
            String patternDescription,
            Boolean imageGenerated,
            BigDecimal referenceAverageReturn,
            BigDecimal referenceMedianReturn,
            BigDecimal referencePositiveRate,
            BigDecimal referenceWorstReturn
    ) {
        this.period = period;
        this.patternId = patternId;
        this.rawPattern = rawPattern;
        this.patternName = patternName;
        this.patternCategory = patternCategory;
        this.confidence = confidence;
        this.patternDescription = patternDescription;
        this.imageGenerated = imageGenerated;
        this.referenceAverageReturn = referenceAverageReturn;
        this.referenceMedianReturn = referenceMedianReturn;
        this.referencePositiveRate = referencePositiveRate;
        this.referenceWorstReturn = referenceWorstReturn;
    }

    public void setAnalysisRun(ChartPatternAnalysisRun analysisRun) {
        this.analysisRun = analysisRun;
    }

    public String getPeriod() {
        return period;
    }

    public Integer getPatternId() {
        return patternId;
    }

    public String getRawPattern() {
        return rawPattern;
    }

    public String getPatternName() {
        return patternName;
    }

    public String getPatternCategory() {
        return patternCategory;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getPatternDescription() {
        return patternDescription;
    }

    public Boolean getImageGenerated() {
        return imageGenerated;
    }

    public BigDecimal getReferenceAverageReturn() {
        return referenceAverageReturn;
    }

    public BigDecimal getReferenceMedianReturn() {
        return referenceMedianReturn;
    }

    public BigDecimal getReferencePositiveRate() {
        return referencePositiveRate;
    }

    public BigDecimal getReferenceWorstReturn() {
        return referenceWorstReturn;
    }
}
