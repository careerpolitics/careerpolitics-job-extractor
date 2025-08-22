package com.careerpolitics.scraper.repository;


import com.careerpolitics.scraper.model.JobSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JobSummaryRepository extends JpaRepository<JobSummary, Long> {
    boolean existsByUrl(String url);

    List<JobSummary> findByProcessedFalse();

    @Query("SELECT js FROM JobSummary js WHERE js.processed = false ORDER BY js.discoveredAt DESC")
    List<JobSummary> findUnprocessedJobs();

    @Query("SELECT js FROM JobSummary js WHERE js.processed = false ORDER BY js.discoveredAt DESC LIMIT :limit")
    List<JobSummary> findUnprocessedJobs(@Param("limit") int limit);

    List<JobSummary> findBySourceWebsite(String sourceWebsite);

    int countByProcessedFalse();

    @Query("SELECT js FROM JobSummary js WHERE js.retryCount < :maxRetries AND js.processed = false")
    List<JobSummary> findRetryableJobs(@Param("maxRetries") int maxRetries);
}
