package com.careerpolitics.scraper.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.*;
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

            for (int attempt = 1; attempt <= 3; attempt++) {
                driver.get(url);

                new WebDriverWait(driver, Duration.ofSeconds(Math.max(8, timeoutSeconds)))
                        .until(d -> d.getPageSource() != null && d.getPageSource().contains("<body"));

                // Wait for trend-like DOM content (table rows / data attributes / known classes).
                new WebDriverWait(driver, Duration.ofSeconds(Math.max(8, timeoutSeconds)))
                        .until(d -> !d.findElements(By.cssSelector("table tr, [data-row-id], [data-term], .mZ3RIc, .QNIh4d")).isEmpty());

                if (driver instanceof JavascriptExecutor js) {
                    js.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.5);");
                    Thread.sleep(1000);
                }

                String html = driver.getPageSource();
                Document doc = Jsoup.parse(html);
                List<String> trends = extractor.extractTrendsFromDocument(doc, maxTrends);
                if (!trends.isEmpty()) {
                    log.info("Selenium trends scrape returned {} trends on attempt {}", trends.size(), attempt);
                    return trends;
                }

                log.warn("Selenium scrape attempt {} returned 0 trends", attempt);
                Thread.sleep(1200);
            }

            return List.of();
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
