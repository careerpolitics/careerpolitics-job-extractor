package com.careerpolitics.scraper.service;

import com.careerpolitics.scraper.model.response.TrendNewsItem;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

@Slf4j
@Component
public class SeleniumTrendScraper {

    private static final String REALISTIC_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

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

    @Value("${careerpolitics.content.selenium.max-attempts:5}")
    private int maxAttempts;

    @Value("${careerpolitics.content.selenium.proxy-pool:}")
    private String proxyPool;

    private final Random random = new Random();

    @Value("${careerpolitics.content.selenium.persistent-profile-enabled:false}")
    private boolean persistentProfileEnabled;

    @Value("${careerpolitics.content.selenium.user-data-dir:profile}")
    private String seleniumUserDataDir;


    public List<String> scrapeTrends(String trendsUrl, String geo, String language, int maxTrends, TrendArticleService extractor) {
        if (!seleniumEnabled) {
            return List.of();
        }

        String url = trendsUrl + "?geo=" + extractor.urlEncodePublic(geo) + "&hl=" + extractor.urlEncodePublic(language)
                + "&category=9&status=active";

        WebDriverManager.chromedriver().setup();

        int effectiveAttempts = seleniumHeadless ? Math.max(1, maxAttempts) : 1;
        for (int attempt = 1; attempt <= effectiveAttempts; attempt++) {
            WebDriver driver = null;
            DriverSession driverSession = null;
            try {
                driverSession = createStealthChromeDriver(null, attempt);
                driver = driverSession.driver();
                driver.get(url);

                new WebDriverWait(driver, Duration.ofSeconds(Math.max(8, timeoutSeconds)))
                        .until(d -> d.getPageSource() != null && d.getPageSource().contains("<body"));

                simulateHumanBrowsing(driver, null);

                new WebDriverWait(driver, Duration.ofSeconds(Math.max(8, timeoutSeconds)))
                        .until(d -> !d.findElements(By.cssSelector("table tr, [data-row-id], [data-term], .mZ3RIc, .QNIh4d")).isEmpty());

                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.5);");
                Thread.sleep(1000);

                String html = driver.getPageSource();
                Document doc = Jsoup.parse(html);
                List<String> trends = extractor.extractTrendsFromDocument(doc, maxTrends);
                if (!trends.isEmpty()) {
                    log.info("Selenium trends scrape returned {} trends on attempt {}", trends.size(), attempt);
                    return trends;
                }

                log.warn("Selenium trends scrape attempt {} returned 0 trends", attempt);
            } catch (SessionNotCreatedException ex) {
                log.warn("Selenium trends session could not be created on attempt {}: {}", attempt, ex.getMessage());
                break;
            } catch (Exception ex) {
                log.warn("Selenium trends scrape attempt {} failed: {}", attempt, ex.getMessage());
            } finally {
                quitDriver(driver);
                cleanupProfileDir(driverSession);
            }

            randomSleep(800, 2200);
        }

        return List.of();
    }

    public List<TrendNewsItem> scrapeGoogleSearchNews(String searchUrl, String trend, int maxArticlesPerTrend, TrendArticleService extractor) {
        if (!seleniumEnabled || !seleniumNewsEnabled) {
            return List.of();
        }

        WebDriverManager.chromedriver().setup();
        ProxyManager proxyManager = new ProxyManager(parseProxyPool(proxyPool));

        int effectiveAttempts = seleniumHeadless ? Math.max(1, maxAttempts) : 1;
        for (int attempt = 1; attempt <= effectiveAttempts; attempt++) {
            String proxy = proxyManager.acquireProxy();
            WebDriver driver = null;
            DriverSession driverSession = null;
            try {
                driverSession = createStealthChromeDriver(proxy, attempt);
                driver = driverSession.driver();
                driver.get(searchUrl);

                new WebDriverWait(driver, Duration.ofSeconds(Math.max(8, timeoutSeconds)))
                        .until(d -> d.getPageSource() != null && d.getPageSource().contains("<body"));

                // Early bot-check detection and immediate proxy rotation.
                if (isLikelyBotCheckPage(driver)) {
                    log.warn("Bot-check immediately detected for trend={} attempt={} proxy={}", trend, attempt, proxy);
                    proxyManager.markBad(proxy);
                    continue;
                }

                dismissConsentIfPresent(driver);
                ensureNewsTab(driver, searchUrl);
                simulateHumanBrowsing(driver, trend);

                boolean verificationCleared = waitForManualVerificationIfNeeded(driver, trend);
                if (!verificationCleared) {
                    proxyManager.markBad(proxy);
                    continue;
                }

                if (isLikelyBotCheckPage(driver)) {
                    log.warn("Bot-check persisted after interaction for trend={} attempt={} proxy={}", trend, attempt, proxy);
                    proxyManager.markBad(proxy);
                    continue;
                }

                clickShowMoreIfPresent(driver);

                new WebDriverWait(driver, Duration.ofSeconds(Math.max(8, timeoutSeconds)))
                        .until(d -> !d.findElements(By.cssSelector("div.SoaBEf, div.dbsr, div.MjjYud, g-card, article, a.WlydOe")).isEmpty());

                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.75);");
                Thread.sleep(1200);

                String html = driver.getPageSource();
                List<TrendNewsItem> newsItems = extractor.parseGoogleSearchNewsDocument(Jsoup.parse(html, searchUrl), trend, maxArticlesPerTrend);
                if (!newsItems.isEmpty()) {
                    log.info("Selenium Google Search news scrape returned {} items for trend={} on attempt {}", newsItems.size(), trend, attempt);
                    return newsItems;
                }

                log.warn("Selenium Google Search news scrape attempt {} returned 0 items for trend={}", attempt, trend);
            } catch (SessionNotCreatedException ex) {
                log.warn("Selenium session could not be created for trend={} attempt={} proxy={}: {}", trend, attempt, proxy, ex.getMessage());
                proxyManager.markBad(proxy);
                break;
            } catch (Exception ex) {
                log.warn("Selenium Google Search news scrape attempt {} failed for trend={}: {}", attempt, trend, ex.getMessage());
                proxyManager.markBad(proxy);
            } finally {
                quitDriver(driver);
                cleanupProfileDir(driverSession);
            }

            randomSleep(1000, 3000);
        }

        log.warn("Selenium scraping failed after retries for trend={}. Falling back to Google News RSS.", trend);
        return scrapeGoogleNewsViaRss(trend, searchUrl, maxArticlesPerTrend, extractor);
    }

    private DriverSession createStealthChromeDriver(String proxy, int attempt) throws Exception {
        Path profileDir = resolveProfileDir(attempt);
        ChromeOptions options = buildChromeOptions(proxy, profileDir);
        ChromeDriver driver = new ChromeDriver(options);
        applyStealthCdp(driver);
        return new DriverSession(driver, profileDir);
    }

    private ChromeOptions buildChromeOptions(String proxy, Path profileDir) {
        ChromeOptions options = new ChromeOptions();
        if (seleniumHeadless) {
            options.addArguments("--headless=new");
        }

        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--lang=en-US,en;q=0.9");
        options.addArguments("--user-agent=" + REALISTIC_USER_AGENT);
        options.addArguments("--user-data-dir=" + profileDir.toAbsolutePath());
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        if (proxy != null && !proxy.isBlank()) {
            options.addArguments("--proxy-server=http://" + proxy);
        }

        return options;
    }

    private void applyStealthCdp(ChromeDriver driver) {
        try {
            driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", java.util.Map.of(
                    "source", "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
                            + "Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});"
                            + "Object.defineProperty(navigator, 'language', {get: () => 'en-US'});"
                            + "Object.defineProperty(navigator, 'languages', {get: () => ['en-US','en']});"
            ));
            driver.executeCdpCommand("Network.setUserAgentOverride", java.util.Map.of(
                    "userAgent", REALISTIC_USER_AGENT,
                    "acceptLanguage", "en-US,en;q=0.9",
                    "platform", "Windows"
            ));
        } catch (Exception ex) {
            log.debug("Failed to apply stealth CDP commands: {}", ex.getMessage());
        }
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
                    buttons.getFirst().click();
                    randomSleep(500, 1200);
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
                newsTab.getFirst().click();
                randomSleep(700, 1500);
                return;
            }

            if (!searchUrl.contains("tbm=nws")) {
                String forcedNewsUrl = searchUrl + (searchUrl.contains("?") ? "&" : "?")
                        + "tbm=nws&udm=14&num=" + URLEncoder.encode("20", StandardCharsets.UTF_8);
                driver.get(forcedNewsUrl);
                randomSleep(700, 1500);
            }
        } catch (Exception ex) {
            log.debug("Could not force news tab: {}", ex.getMessage());
        }
    }

    private boolean waitForManualVerificationIfNeeded(WebDriver driver, String trend) {
        if (!isLikelyBotCheckPage(driver)) {
            return true;
        }

        if (!manualVerificationWaitEnabled) {
            log.warn("Bot-check detected for trend={} and manual verification wait is disabled.", trend);
            return false;
        }

        if (seleniumHeadless) {
            log.warn("Manual verification wait is enabled but Selenium is headless. Set SELENIUM_HEADLESS=false to solve bot checks interactively.");
        }

        int maxWait = Math.max(30, manualVerificationMaxWaitSeconds);
        log.warn("Google bot-check detected for trend={}. Please complete verification in browser. Waiting up to {} seconds...", trend, maxWait);

        long deadline = System.currentTimeMillis() + (maxWait * 1000L);
        while (System.currentTimeMillis() < deadline) {
            try {
                if (!isLikelyBotCheckPage(driver)) {
                    log.info("Bot-check cleared by user for trend={}", trend);
                    return true;
                }
                randomSleep(800, 1600);
            } catch (Exception ignored) {
            }
        }

        log.warn("Timed out waiting for manual bot-check verification for trend={}", trend);
        return false;
    }

    private boolean isLikelyBotCheckPage(WebDriver driver) {
        try {
            String title = String.valueOf(driver.getTitle()).toLowerCase(Locale.ROOT);
            String body = String.valueOf(driver.getPageSource()).toLowerCase(Locale.ROOT);

            boolean hasCaptchaFrame = !driver.findElements(By.cssSelector(
                    "iframe[src*='recaptcha'], iframe[src*='captcha'], iframe[title*='challenge'], iframe[src*='sorry'], iframe[src*='hcaptcha']"
            )).isEmpty();
            boolean hasCaptchaInputs = !driver.findElements(By.cssSelector(
                    "input[name='captcha'], textarea[g-recaptcha-response], div.g-recaptcha, div.h-captcha"
            )).isEmpty();
            boolean hasNewsCards = !driver.findElements(By.cssSelector("div.SoaBEf, div.dbsr, div.MjjYud, g-card, article, a.WlydOe")).isEmpty();

            boolean challengeText = title.contains("unusual traffic")
                    || title.contains("sorry")
                    || title.contains("captcha")
                    || body.contains("verify you are human")
                    || body.contains("i'm not a robot")
                    || body.contains("unusual traffic")
                    || body.contains("complete the captcha")
                    || body.contains("g-recaptcha")
                    || body.contains("detected unusual traffic")
                    || body.contains("our systems have detected")
                    || body.contains("please show you're not a robot");

            return (hasCaptchaFrame || hasCaptchaInputs || challengeText) && !hasNewsCards;
        } catch (Exception ex) {
            return false;
        }
    }

    private void simulateHumanBrowsing(WebDriver driver, String trend) {
        try {
            incrementalScroll(driver);
            randomMouseMovements(driver);
            if (trend != null && !trend.isBlank()) {
                randomSleep(900, 2200);
            }
        } catch (Exception ex) {
            log.debug("Human-like interaction simulation failed: {}", ex.getMessage());
        }
    }

    private void incrementalScroll(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor js)) {
            return;
        }

        for (int step : List.of(20, 40, 60, 80)) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight * arguments[0] / 100);", step);
            randomSleep(700, 1700);
        }
    }

    private void randomMouseMovements(WebDriver driver) {
        try {
            List<WebElement> cards = driver.findElements(By.cssSelector("div.SoaBEf, div.dbsr, div.MjjYud, g-card, article"));
            if (cards.isEmpty()) {
                return;
            }

            int moves = Math.min(3, cards.size());
            Actions actions = new Actions(driver);
            for (int i = 0; i < moves; i++) {
                WebElement card = cards.get(random.nextInt(cards.size()));
                actions.moveToElement(card).pause(Duration.ofMillis(200 + random.nextInt(500))).perform();
                randomSleep(400, 1100);
            }
        } catch (Exception ex) {
            log.debug("Random mouse movement simulation failed: {}", ex.getMessage());
        }
    }

    private void clickShowMoreIfPresent(WebDriver driver) {
        try {
            List<By> selectors = List.of(
                    By.xpath("//span[contains(text(),'More')]/ancestor::a[1]"),
                    By.xpath("//button[contains(.,'More') or contains(.,'Show more') or contains(.,'More news')]")
            );

            for (By selector : selectors) {
                List<WebElement> buttons = driver.findElements(selector);
                if (!buttons.isEmpty()) {
                    buttons.get(0).click();
                    randomSleep(1000, 2200);
                    return;
                }
            }
        } catch (Exception ex) {
            log.debug("No show-more action performed: {}", ex.getMessage());
        }
    }

    private List<TrendNewsItem> scrapeGoogleNewsViaRss(String trend,
                                                       String searchUrl,
                                                       int maxArticlesPerTrend,
                                                       TrendArticleService extractor) {
        try {
            String language = "en-US";
            String geo = "US";
            String query = trend;

            String rssUrl = "https://news.google.com/rss/search?q=" + extractor.urlEncodePublic(query)
                    + "&hl=" + extractor.urlEncodePublic(language)
                    + "&gl=" + extractor.urlEncodePublic(geo)
                    + "&ceid=" + extractor.urlEncodePublic(geo + ":en");

            String xml = Jsoup.connect(rssUrl)
                    .ignoreContentType(true)
                    .userAgent(REALISTIC_USER_AGENT)
                    .timeout(Math.max(8000, timeoutSeconds * 1000))
                    .execute()
                    .body();

            Document rssDoc = Jsoup.parse(xml, searchUrl, Parser.xmlParser());
            List<TrendNewsItem> fallbackItems = new ArrayList<>();
            for (Element item : rssDoc.select("item")) {
                String link = item.selectFirst("link") != null ? item.selectFirst("link").text() : "";
                String resolvedLink = extractor.resolveOriginalNewsUrl(link);
                String title = item.selectFirst("title") != null ? item.selectFirst("title").text() : "";
                String source = item.selectFirst("source") != null ? item.selectFirst("source").text() : "Google News";
                String publishedAt = item.selectFirst("pubDate") != null ? item.selectFirst("pubDate").text() : "";
                String descriptionHtml = item.selectFirst("description") != null ? item.selectFirst("description").text() : "";
                String snippet = Jsoup.parse(descriptionHtml).text();

                fallbackItems.add(TrendNewsItem.builder()
                        .trend(trend)
                        .title(title)
                        .link(resolvedLink == null || resolvedLink.isBlank() ? link : resolvedLink)
                        .source(source)
                        .publishedAt(publishedAt)
                        .snippet(snippet)
                        .build());

                if (fallbackItems.size() >= maxArticlesPerTrend) {
                    break;
                }
            }

            log.info("RSS fallback returned {} items for trend={}", fallbackItems.size(), trend);
            return fallbackItems;
        } catch (Exception ex) {
            log.warn("RSS fallback failed for trend={}: {}", trend, ex.getMessage());
            return List.of();
        }
    }

    private void quitDriver(WebDriver driver) {
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (Exception ignored) {
        }
    }

    private void randomSleep(int minMs, int maxMs) {
        int low = Math.max(0, minMs);
        int high = Math.max(low + 1, maxMs);
        try {
            Thread.sleep(low + random.nextInt(high - low));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> parseProxyPool(String pool) {
        if (pool == null || pool.isBlank()) {
            return List.of();
        }

        return Arrays.stream(pool.split(","))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .toList();
    }

    private Path resolveProfileDir(int attempt) throws Exception {
        if (persistentProfileEnabled) {
            Path base = Path.of(seleniumUserDataDir).toAbsolutePath();
            Files.createDirectories(base);
            return base;
        }

        Path tempDir = Files.createTempDirectory("selenium-profile-" + attempt + "-");
        tempDir.toFile().deleteOnExit();
        return tempDir;
    }

    private void cleanupProfileDir(DriverSession session) {
        if (session == null || session.profileDir() == null || persistentProfileEnabled) {
            return;
        }

        try {
            Files.walk(session.profileDir())
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private record DriverSession(ChromeDriver driver, Path profileDir) {}

    private class ProxyManager {
        private final List<String> proxies;
        private final Set<String> badProxies = new HashSet<>();

        private ProxyManager(List<String> proxies) {
            this.proxies = proxies;
        }

        String acquireProxy() {
            if (proxies.isEmpty()) {
                return "";
            }

            List<String> healthy = proxies.stream().filter(p -> !badProxies.contains(p)).toList();
            if (healthy.isEmpty()) {
                badProxies.clear();
                return proxies.get(random.nextInt(proxies.size()));
            }
            return healthy.get(random.nextInt(healthy.size()));
        }

        void markBad(String proxy) {
            if (proxy == null || proxy.isBlank()) {
                return;
            }
            badProxies.add(proxy);
            log.warn("Marked proxy as bad: {}", proxy);
        }
    }
}
