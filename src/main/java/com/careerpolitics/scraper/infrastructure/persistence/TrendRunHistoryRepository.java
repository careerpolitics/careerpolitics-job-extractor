package com.careerpolitics.scraper.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Set;

public interface TrendRunHistoryRepository extends JpaRepository<TrendRunHistory, Long> {

    @Query("select history.trendSlug from TrendRunHistory history where history.generatedAt >= :cutoff")
    Set<String> findTrendSlugsUsedSince(@Param("cutoff") LocalDateTime cutoff);
}
