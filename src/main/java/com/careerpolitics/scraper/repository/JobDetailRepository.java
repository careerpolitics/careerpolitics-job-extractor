package com.careerpolitics.scraper.repository;


import com.careerpolitics.scraper.model.JobDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JobDetailRepository extends JpaRepository<JobDetail, Long> {
    List<JobDetail> findByOrderByScrapedAtDesc();

    Page<JobDetail> findAll(Pageable pageable);

    boolean existsByUrl(String url);

    @Query("SELECT jd FROM JobDetail jd WHERE jd.department = :department")
    Page<JobDetail> findByDepartment(@Param("department") String department, Pageable pageable);

    @Query("SELECT jd FROM JobDetail jd WHERE jd.sourceWebsite = :website")
    Page<JobDetail> findBySourceWebsite(@Param("website") String website, Pageable pageable);

    @Query("SELECT jd FROM JobDetail jd WHERE jd.isActive = true AND (jd.expiryDate IS NULL OR jd.expiryDate > CURRENT_TIMESTAMP)")
    Page<JobDetail> findActiveJobs(Pageable pageable);

    @Query("SELECT COUNT(jd) FROM JobDetail jd WHERE jd.isActive = true")
    int countActiveJobs();

    @Query("SELECT COUNT(jd) FROM JobDetail jd WHERE jd.expiryDate IS NOT NULL AND jd.expiryDate < CURRENT_TIMESTAMP")
    int countExpiredJobs();
}
