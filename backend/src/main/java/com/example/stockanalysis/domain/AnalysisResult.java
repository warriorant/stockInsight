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
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_results")
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(nullable = false, length = 1000)
    private String technicalAnalysis;

    @Column(nullable = false, length = 1000)
    private String fundamentalAnalysis;

    @Column(nullable = false, length = 1000)
    private String risk;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false, length = 32)
    private String rating;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AnalysisResult() {
    }

    public AnalysisResult(
            Stock stock,
            String symbol,
            String summary,
            String technicalAnalysis,
            String fundamentalAnalysis,
            String risk,
            Integer score,
            String rating
    ) {
        this.stock = stock;
        this.symbol = symbol;
        this.summary = summary;
        this.technicalAnalysis = technicalAnalysis;
        this.fundamentalAnalysis = fundamentalAnalysis;
        this.risk = risk;
        this.score = score;
        this.rating = rating;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Stock getStock() {
        return stock;
    }

    public void setStock(Stock stock) {
        this.stock = stock;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getTechnicalAnalysis() {
        return technicalAnalysis;
    }

    public void setTechnicalAnalysis(String technicalAnalysis) {
        this.technicalAnalysis = technicalAnalysis;
    }

    public String getFundamentalAnalysis() {
        return fundamentalAnalysis;
    }

    public void setFundamentalAnalysis(String fundamentalAnalysis) {
        this.fundamentalAnalysis = fundamentalAnalysis;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

