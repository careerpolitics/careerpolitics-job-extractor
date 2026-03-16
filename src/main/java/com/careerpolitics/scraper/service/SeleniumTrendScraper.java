package com.careerpolitics.scraper.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class SeleniumTrendScraper {

    @Value("${careerpolitics.content.selenium.enabled:true}")
    private boolean seleniumEnabled;

    @Value("${careerpolitics.content.selenium.timeout-seconds:20}")
    private int timeoutSeconds;

    @Value("${careerpolitics.content.selenium.news-enabled:true}")
    private boolean seleniumNewsEnabled;

    @Value("${careerpolitics.content.selenium.headless:false}")
    private boolean seleniumHeadless;

    @Value("${careerpolitics.content.selenium.manual-verification-wait-enabled:true}")
    private boolean manualVerificationWaitEnabled;

    @Value("${careerpolitics.content.selenium.manual-verification-max-wait-seconds:180}")
    private int manualVerificationMaxWaitSeconds;

    public List<String> scrapeTrends(String trendsUrl, String geo, String language, int maxTrends, TrendArticleService extractor) {
        if (!seleniumEnabled) {
            return List.of();
        }

        String url = trendsUrl + "?geo=" + extractor.urlEncodePublic(geo) + "&hl=" + extractor.urlEncodePublic(language)
                + "&category=9&status=active";

        WebDriver driver = null;
        try {
            WebDriverManager.chromedriver().setup();

            driver = new ChromeDriver(buildChromeOptions());

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

    public List<TrendNewsItem> scrapeGoogleSearchNews(String searchUrl, String trend, int maxArticlesPerTrend, TrendArticleService extractor) {
        if (!seleniumEnabled || !seleniumNewsEnabled) {
            return List.of();
        }

        WebDriver driver = null;
        try {
            WebDriverManager.chromedriver().setup();

            driver = new ChromeDriver(buildChromeOptions());

            for (int attempt = 1; attempt <= 3; attempt++) {
                driver.get(searchUrl);

                new WebDriverWait(driver, Duration.ofSeconds(Math.max(8, timeoutSeconds)))
                        .until(d -> d.getPageSource() != null && d.getPageSource().contains("<body"));

                dismissConsentIfPresent(driver);
                ensureNewsTab(driver, searchUrl);
                waitForManualVerificationIfNeeded(driver, trend);

                new WebDriverWait(driver, Duration.ofSeconds(Math.max(8, timeoutSeconds)))
                        .until(d -> !d.findElements(By.cssSelector("div.SoaBEf, div.dbsr, div.MjjYud, g-card, article, a.WlydOe")).isEmpty());

                if (driver instanceof JavascriptExecutor js) {
                    js.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.75);");
                    Thread.sleep(1200);
                }

                String html = driver.getPageSource();
                List<TrendNewsItem> newsItems = extractor.parseGoogleSearchNewsDocument(Jsoup.parse(html, searchUrl), trend, maxArticlesPerTrend);
                if (!newsItems.isEmpty()) {
                    log.info("Selenium Google Search news scrape returned {} items for trend={} on attempt {}", newsItems.size(), trend, attempt);
                    return newsItems;
                }

                log.warn("Selenium Google Search news scrape attempt {} returned 0 items for trend={}", attempt, trend);
                Thread.sleep(1200);
            }

            return List.of();
        } catch (Exception ex) {
            log.warn("Selenium Google Search news scrape failed for trend={}: {}", trend, ex.getMessage());
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

    private ChromeOptions buildChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        if (seleniumHeadless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        return options;
    }

    private void dismissConsentIfPresent(WebDriver driver) {
        try {
            List<By> consentButtons = List.of(
                    By.cssSelector("button[aria-label*='Accept']"),
                    By.cssSelector("button[aria-label*='I agree']"),
                    By.cssSelector("button#L2AGLb"),
                    By.xpath("//button[contains(., 'Accept all') or contains(., 'I agree')]")
            );
            for (By by : consentButtons) {
                List<WebElement> buttons = driver.findElements(by);
                if (!buttons.isEmpty()) {
                    buttons.get(0).click();
                    Thread.sleep(700);
                    return;
                }
            }
        } catch (Exception ex) {
            log.debug("No consent dialog action needed: {}", ex.getMessage());
        }
    }

    private void ensureNewsTab(WebDriver driver, String searchUrl) {
        try {
            boolean hasNewsCards = !driver.findElements(By.cssSelector("div.SoaBEf, div.dbsr, div.MjjYud, g-card, article")).isEmpty();
            if (hasNewsCards) {
                return;
            }

            List<WebElement> newsTab = driver.findElements(By.cssSelector("a[href*='tbm=nws']"));
            if (!newsTab.isEmpty()) {
                newsTab.get(0).click();
                Thread.sleep(900);
                return;
            }

            if (!searchUrl.contains("tbm=nws")) {
                String forcedNewsUrl = searchUrl + (searchUrl.contains("?") ? "&" : "?")
                        + "tbm=nws&udm=14&num=" + URLEncoder.encode("20", StandardCharsets.UTF_8);
                driver.get(forcedNewsUrl);
                Thread.sleep(900);
            }
        } catch (Exception ex) {
            log.debug("Could not force news tab: {}", ex.getMessage());
        }
    }


    private void waitForManualVerificationIfNeeded(WebDriver driver, String trend) {
        if (!manualVerificationWaitEnabled) {
            return;
        }

        if (seleniumHeadless) {
            log.warn("Manual verification wait is enabled but Selenium is headless. Set SELENIUM_HEADLESS=false to solve bot checks interactively.");
        }

        if (!isLikelyBotCheckPage(driver)) {
            return;
        }

        int maxWait = Math.max(30, manualVerificationMaxWaitSeconds);
        log.warn("Google bot-check detected for trend={}. Please complete 'I'm human' verification in browser. Waiting up to {} seconds...", trend, maxWait);

        long deadline = System.currentTimeMillis() + (maxWait * 1000L);
        while (System.currentTimeMillis() < deadline) {
            try {
                if (!isLikelyBotCheckPage(driver)) {
                    log.info("Bot-check cleared by user for trend={}.", trend);
                    return;
                }
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
            }
        }

        log.warn("Timed out waiting for manual bot-check verification for trend={}", trend);
    }

    private boolean isLikelyBotCheckPage(WebDriver driver) {
        try {
            String title = String.valueOf(driver.getTitle()).toLowerCase(Locale.ROOT);
            String body = String.valueOf(driver.getPageSource()).toLowerCase(Locale.ROOT);

            boolean hasChallengeFrame = !driver.findElements(By.cssSelector("iframe[src*='recaptcha'], iframe[title*='challenge'], iframe[src*='sorry']")).isEmpty();
            boolean hasNewsCards = !driver.findElements(By.cssSelector("div.SoaBEf, div.dbsr, div.MjjYud, g-card, article, a.WlydOe")).isEmpty();

            boolean challengeText = title.contains("unusual traffic")
                    || title.contains("sorry")
                    || body.contains("verify you are human")
                    || body.contains("i'm not a robot")
                    || body.contains("unusual traffic")
                    || body.contains("complete the captcha")
                    || body.contains("g-recaptcha");

            return (hasChallengeFrame || challengeText) && !hasNewsCards;
        } catch (Exception ex) {
            return false;
        }
    }

}
