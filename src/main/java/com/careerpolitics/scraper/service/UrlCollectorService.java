package com.careerpolitics.scraper.service;


import com.careerpolitics.scraper.model.JobSummary;
import com.careerpolitics.scraper.repository.JobSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCollectorService {

    private static final String BASE_URL = "https://www.sarkariexam.com";
    private static final Pattern JOB_URL_PATTERN = Pattern.compile(
            "https://www\\.sarkariexam\\.com/[^\"']*?(recruitment|form|notification|apply)[^\"']*"
    );

    private final JobSummaryRepository jobSummaryRepository;

    public List<JobSummary> collectJobUrls() {
        List<JobSummary> newJobs = new ArrayList<>();

        try {
            log.info("Starting URL collection from homepage...");

            // Fetch homepage content
            Document doc = Jsoup.connect(BASE_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            // Extract job links
            Elements jobLinks = doc.select("a[href*='sarkariexam.com']");

            for (Element link : jobLinks) {
                String href = link.attr("href");
                String title = link.text().trim();

                if (isValidJobLink(title, href) && !jobSummaryRepository.existsByUrl(href)) {
                    JobSummary jobSummary = new JobSummary();
                    jobSummary.setTitle(title);
                    jobSummary.setUrl(href);
                    jobSummary.setSourceWebsite(BASE_URL);
                    jobSummary.setDiscoveredAt(LocalDateTime.now());
                    jobSummary.setProcessed(false);

                    jobSummaryRepository.save(jobSummary);
                    newJobs.add(jobSummary);

                    log.debug("Discovered new job: {}", title);
                }
            }

            log.info("URL collection completed. Found {} new jobs", newJobs.size());

        } catch (IOException e) {
            log.error("Failed to scrape sarkariexam.com: {}", e.getMessage());
        }

        return newJobs;
    }

    private boolean isValidJobLink(String title, String href) {
        return href.contains("recruitment") ||
                href.contains("form") ||
                href.contains("notification") ||
                (title.matches(".*\\d+.*[Pp]ost.*") && href.contains("sarkariexam.com"));
    }
}
