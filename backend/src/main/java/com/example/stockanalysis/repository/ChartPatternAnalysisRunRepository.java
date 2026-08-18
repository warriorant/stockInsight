package com.example.stockanalysis.repository;

import com.example.stockanalysis.domain.ChartPatternAnalysisRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartPatternAnalysisRunRepository extends JpaRepository<ChartPatternAnalysisRun, Long> {

    @EntityGraph(attributePaths = "periodResults")
    Optional<ChartPatternAnalysisRun> findFirstBySymbolOrderByCreatedAtDesc(String symbol);
}
