package com.careerpolitics.scraper.repository;

import com.careerpolitics.scraper.model.TrendArticleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TrendArticleHistoryRepository extends JpaRepository<TrendArticleHistory, Long> {

    @Query("select h.trendSlug from TrendArticleHistory h where h.generatedAt >= :cutoff")
    List<String> findTrendSlugsUsedSince(@Param("cutoff") LocalDateTime cutoff);

}
