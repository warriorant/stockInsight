package com.example.stockanalysis.repository;

import com.example.stockanalysis.domain.Stock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findBySymbol(String symbol);

    List<Stock> findAllByOrderByNameAsc();

    @Query("""
            select s
            from Stock s
            where lower(s.symbol) like lower(concat('%', :keyword, '%'))
               or lower(s.externalSymbol) like lower(concat('%', :keyword, '%'))
               or lower(s.name) like lower(concat('%', :keyword, '%'))
               or lower(s.market) like lower(concat('%', :keyword, '%'))
               or lower(s.sector) like lower(concat('%', :keyword, '%'))
               or lower(s.industry) like lower(concat('%', :keyword, '%'))
            order by s.name asc
            """)
    List<Stock> search(@Param("keyword") String keyword, Pageable pageable);
}
