package com.careerpolitics.scraper.service;


import com.careerpolitics.scraper.model.JobDetail;
import com.careerpolitics.scraper.repository.JobDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobDataService {

    private final JobDetailRepository jobDetailRepository;
    private final MarkdownGenerator markdownGenerator;

    public Page<JobDetail> getJobs(int page, int size, String department, String website, Boolean activeOnly) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        if (Boolean.TRUE.equals(activeOnly)) {
            return jobDetailRepository.findActiveJobs(pageable);
        }
        if (department != null && !department.isBlank()) {
            return jobDetailRepository.findByDepartment(department, pageable);
        }
        if (website != null && !website.isBlank()) {
            return jobDetailRepository.findBySourceWebsite(website, pageable);
        }
        return jobDetailRepository.findAll(pageable);
    }

    public Optional<JobDetail> getJobById(Long id) {
        return jobDetailRepository.findById(id);
    }

    public Optional<String> getJobMarkdown(Long id) {
        return jobDetailRepository.findById(id).map(detail -> {
            String md = markdownGenerator.generate(detail);
            detail.setMarkdownContent(md);
            jobDetailRepository.save(detail);
            return md;
        });
    }

    public void deleteJob(Long id) {
        jobDetailRepository.deleteById(id);
    }

    public Map<String, Object> exportJobs(String format, String department) {
        List<JobDetail> all;
        if (department != null && !department.isBlank()) {
            all = jobDetailRepository.findByDepartment(department, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        } else {
            all = jobDetailRepository.findAll();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", all.size());
        result.put("format", format);
        return result;
    }

    public List<String> getDepartments() {
        return jobDetailRepository.findAll().stream()
                .map(JobDetail::getDepartment)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getWebsites() {
        return jobDetailRepository.findAll().stream()
                .map(JobDetail::getSourceWebsite)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public JobDetail toggleJobActiveStatus(Long id) {
        JobDetail detail = jobDetailRepository.findById(id).orElseThrow();
        detail.setActive(!detail.isActive());
        return jobDetailRepository.save(detail);
    }

    public Map<String, Object> getDataStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", jobDetailRepository.count());
        stats.put("active", jobDetailRepository.countActiveJobs());
        stats.put("expired", jobDetailRepository.countExpiredJobs());
        return stats;
    }
}
