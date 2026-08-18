package com.example.stockanalysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "stock_candles",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_candle_symbol_date_source", columnNames = {"symbol", "candle_date", "source"})
)
public class StockCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "candle_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal openPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal highPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lowPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal closePrice;

    private Long volume;

    @Column(nullable = false)
    private Boolean adjusted;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StockCandle() {
    }

    public StockCandle(
            String symbol,
            LocalDate date,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            Long volume,
            Boolean adjusted,
            String source
    ) {
        this.symbol = symbol;
        this.date = date;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.adjusted = adjusted;
        this.source = source;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public Boolean getAdjusted() {
        return adjusted;
    }

    public void setAdjusted(Boolean adjusted) {
        this.adjusted = adjusted;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
