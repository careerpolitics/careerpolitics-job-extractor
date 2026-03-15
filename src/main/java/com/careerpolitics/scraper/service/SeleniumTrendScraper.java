package com.careerpolitics.scraper.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class SeleniumTrendScraper {

    @Value("${careerpolitics.content.selenium.enabled:true}")
    private boolean seleniumEnabled;

    @Value("${careerpolitics.content.selenium.timeout-seconds:20}")
    private int timeoutSeconds;

    public List<String> scrapeTrends(String trendsUrl, String geo, String language, int maxTrends, TrendArticleService extractor) {
        if (!seleniumEnabled) {
            return List.of();
        }

        String url = trendsUrl + "?geo=" + extractor.urlEncodePublic(geo) + "&hl=" + extractor.urlEncodePublic(language)
                + "&category=9&status=active";

        WebDriver driver = null;
        try {
            WebDriverManager.chromedriver().setup();

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            driver = new ChromeDriver(options);

            driver.get(url);
            new WebDriverWait(driver, Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                    .until(d -> d.getPageSource() != null && d.getPageSource().contains("<body"));

            String html = driver.getPageSource();
            Document doc = Jsoup.parse(html);
            List<String> trends = extractor.extractTrendsFromDocument(doc, maxTrends);
            log.info("Selenium trends scrape returned {} trends", trends.size());
            return trends;
        } catch (Exception ex) {
            log.warn("Selenium trends scrape failed: {}", ex.getMessage());
            return List.of();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
