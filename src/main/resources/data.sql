-- Seed default scraping config
INSERT INTO scraping_configs (id, default_batch_size, max_retry_attempts, retry_delay_ms, request_delay_ms,
                              enable_caching, cache_timeout_minutes, max_concurrent_scrapers, job_expiry_days,
                              enable_image_processing, image_quality, max_image_size_kb)
VALUES ('default', 5, 3, 5000, 1000, TRUE, 60, 3, 30, TRUE, 85, 500);

-- Seed website config for sarkariexam
INSERT INTO website_configs (name, base_url, url_pattern, title_selector, enabled, priority, request_delay_ms,
                             timeout_ms, max_pages_to_scrape, last_scraped, success_rate)
VALUES ('sarkariexam', 'https://www.sarkariexam.com', 'recruitment|notification|apply|form', 'title', TRUE, 1,
        1000, 10000, 5, NULL, 0.0);