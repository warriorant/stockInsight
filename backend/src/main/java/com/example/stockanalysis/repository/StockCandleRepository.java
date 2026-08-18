package com.example.stockanalysis.repository;

import com.example.stockanalysis.domain.StockCandle;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockCandleRepository extends JpaRepository<StockCandle, Long> {

    Optional<StockCandle> findBySymbolAndDateAndSource(String symbol, LocalDate date, String source);

    List<StockCandle> findBySymbolAndSourceAndDateBetweenOrderByDateAsc(
            String symbol,
            String source,
            LocalDate startDate,
            LocalDate endDate
    );

    long countBySymbolAndSource(String symbol, String source);
}
