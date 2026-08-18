package com.example.stockanalysis.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chart_pattern_analysis_runs")
public class ChartPatternAnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false, length = 64)
    private String analysisMode;

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
    private String summary;

    @Column(length = 2000)
    private String patternDescription;

    @Column(length = 2000)
    private String disclaimer;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "analysisRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("period ASC")
    private List<ChartPatternPeriodResult> periodResults = new ArrayList<>();

    protected ChartPatternAnalysisRun() {
    }

    public ChartPatternAnalysisRun(
            String symbol,
            String name,
            LocalDate targetDate,
            String analysisMode,
            Integer patternId,
            String rawPattern,
            String patternName,
            String patternCategory,
            BigDecimal confidence,
            String summary,
            String patternDescription,
            String disclaimer
    ) {
        this.symbol = symbol;
        this.name = name;
        this.targetDate = targetDate;
        this.analysisMode = analysisMode;
        this.patternId = patternId;
        this.rawPattern = rawPattern;
        this.patternName = patternName;
        this.patternCategory = patternCategory;
        this.confidence = confidence;
        this.summary = summary;
        this.patternDescription = patternDescription;
        this.disclaimer = disclaimer;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getAnalysisMode() {
        return analysisMode;
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

    public String getSummary() {
        return summary;
    }

    public String getPatternDescription() {
        return patternDescription;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<ChartPatternPeriodResult> getPeriodResults() {
        return periodResults;
    }

    public void addPeriodResult(ChartPatternPeriodResult periodResult) {
        periodResult.setAnalysisRun(this);
        this.periodResults.add(periodResult);
    }
}
