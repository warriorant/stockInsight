package com.example.stockanalysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String symbol;

    @Column(length = 32)
    private String externalSymbol;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 32)
    private String market;

    @Column(nullable = false)
    private String sector;

    @Column(nullable = false)
    private String industry;

    @Column(precision = 19, scale = 2)
    private BigDecimal currentPrice;

    @Column(precision = 7, scale = 2)
    private BigDecimal changeRate;

    @Column(length = 1000)
    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected Stock() {
    }

    public Stock(
            String symbol,
            String externalSymbol,
            String name,
            String market,
            String sector,
            String industry,
            BigDecimal currentPrice,
            BigDecimal changeRate,
            String description
    ) {
        this.symbol = symbol;
        this.externalSymbol = externalSymbol;
        this.name = name;
        this.market = market;
        this.sector = sector;
        this.industry = industry;
        this.currentPrice = currentPrice;
        this.changeRate = changeRate;
        this.description = description;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getExternalSymbol() {
        return externalSymbol;
    }

    public void setExternalSymbol(String externalSymbol) {
        this.externalSymbol = externalSymbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getChangeRate() {
        return changeRate;
    }

    public void setChangeRate(BigDecimal changeRate) {
        this.changeRate = changeRate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
