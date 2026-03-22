package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.config.TrendingProperties;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

@Slf4j
@Component
public class SeleniumBrowserClient {

    private static final String STEALTH_SCRIPT = """
            Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
            window.chrome = window.chrome || {runtime: {}};
            Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});
            Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});
            Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});
            Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});
            Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});
            const getParameter = WebGLRenderingContext.prototype.getParameter;
            WebGLRenderingContext.prototype.getParameter = function(parameter) {
              if (parameter === 37445) return 'Intel Inc.';
              if (parameter === 37446) return 'Intel Iris OpenGL Engine';
              return getParameter.call(this, parameter);
            };
            const originalQuery = window.navigator.permissions && window.navigator.permissions.query;
            if (originalQuery) {
              window.navigator.permissions.query = (parameters) => (
                parameters && parameters.name === 'notifications'
                  ? Promise.resolve({ state: Notification.permission })
                  : originalQuery(parameters)
              );
            }
            """;

    private static final By BOT_CHALLENGE_SELECTOR = By.cssSelector(
            "iframe[src*='recaptcha'], iframe[title*='challenge'], iframe[src*='sorry']"
    );

    private final TrendingProperties properties;
    private final Random random = new Random();

    public SeleniumBrowserClient(TrendingProperties properties) {
        this.properties = properties;
    }

    public String fetchTrendsPage(String url) {
        if (!properties.selenium().enabled()) {
            return "";
        }
        return load(url, false, null, WebDriver::getPageSource, "");
    }

    public List<String> fetchTrendTitles(String url, int maxTrends) {
        if (!properties.selenium().enabled()) {
            return List.of();
        }
        return load(url, false, null, driver -> extractTrendTitles(driver, maxTrends), List.of());
    }

    public String fetchNewsPage(String url, String trend) {
        if (!properties.selenium().enabled() || !properties.selenium().newsEnabled()) {
            return "";
        }
        return load(url, true, trend, WebDriver::getPageSource, "");
    }

    private <T> T load(String url, boolean newsMode, String trend, Function<WebDriver, T> extractor, T emptyValue) {
        if (properties.selenium().remoteUrl() == null || properties.selenium().remoteUrl().isBlank()) {
            log.warn("Selenium remote URL is not configured. Returning empty page source.");
            return emptyValue;
        }

        int attempts = Math.max(1, properties.selenium().maxAttempts());
        List<String> proxies = sanitize(properties.selenium().proxyPool());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            String proxy = proxies.isEmpty() ? null : proxies.get((attempt - 1) % proxies.size());
            WebDriver driver = null;
            try {
                driver = createDriver(proxy);
                driver.get(url);

                Duration timeout = Duration.ofSeconds(properties.selenium().timeoutSeconds());
                new WebDriverWait(driver, timeout).until(d -> d.getPageSource() != null && d.getPageSource().contains("<body"));

                dismissConsent(driver);
                if (newsMode) {
                    ensureNewsMode(driver, url);
                }
                performLightInteraction(driver);
                if (newsMode && !waitForManualVerificationIfNeeded(driver, trend)) {
                    continue;
                }

                return extractor.apply(driver);
            } catch (SessionNotCreatedException exception) {
                log.error("Selenium session creation failed on attempt {}: {}", attempt, exception.getMessage(), exception);
                break;
            } catch (Exception exception) {
                log.warn("Selenium page load failed on attempt {} for {}: {}", attempt, url, exception.getMessage());
            } finally {
                if (driver != null) {
                    try {
                        driver.quit();
                    } catch (Exception ignored) {
                    }
                }
            }
            sleep(properties.selenium().sessionRetryBackoff().toMillis());
        }
        return emptyValue;
    }

    private List<String> extractTrendTitles(WebDriver driver, int maxTrends) {
        int desiredCount = Math.max(1, maxTrends);
        for (int attempt = 0; attempt < 6; attempt++) {
            List<String> titles = runTrendExtractionScript(driver, desiredCount);
            if (!titles.isEmpty()) {
                return titles.stream().limit(desiredCount).toList();
            }
            performLightInteraction(driver);
            sleep(properties.selenium().interactionDelayMs());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> runTrendExtractionScript(WebDriver driver, int maxTrends) {
        if (!(driver instanceof JavascriptExecutor javascriptExecutor)) {
            return List.of();
        }
        Object result = javascriptExecutor.executeScript("""
                const maxTrends = arguments[0];
                const rowSelectors = ['table tbody tr', 'tbody tr', '[role="row"]', '[data-row-id]'];
                const titleSelectors = ['[data-term]', '.mZ3RIc', '.QNIh4d', 'a[title]'];
                const metadataMarkers = [
                  'search volume', 'started', 'ended', 'trend breakdown', 'explore link',
                  'copy to clipboard', 'download csv'
                ];
                const noisePatterns = [
                  /^trending_up$/i,
                  /^active$/i,
                  /^\d+(?:[.,]\d+)?(?:[KMB])?\+?\s*searches$/i,
                  /^\d+\s*(?:sec(?:ond)?s?|min(?:ute)?s?|hr|hour|day|week|month)s?\s+ago$/i,
                  /^\d{1,2}:\d{2}\s*(?:am|pm)$/i
                ];
                const seen = new Set();
                const visible = (el) => {
                  if (!el) return false;
                  const style = window.getComputedStyle(el);
                  const rect = el.getBoundingClientRect();
                  return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
                };
                const clean = (value) => (value || '')
                  .replace(/\b(?:trending_up|arrow_upward|timelapse)\b/gi, ' ')
                  .replace(/\bactive\b/gi, ' ')
                  .replace(/\b\d+(?:[.,]\d+)?(?:[KMB])?\+?\s*searches\b/gi, ' ')
                  .replace(/\b(?:\d+\s*(?:sec(?:ond)?s?|min(?:ute)?s?|hr|hour|day|week|month)s?\s+ago|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2}|\d{1,2}:\d{2}\s*(?:am|pm))\b/gi, ' ')
                  .replace(/\b(?:search volume|started|ended|trend breakdown|explore link|copy to clipboard|download csv)\b/gi, ' ')
                  .replace(/\bat\s+\d{1,2}:\d{2}:\d{2}\s*(?:am|pm)?\s*utc(?:[+-]\d{1,2}(?::\d{2})?)?/gi, ' ')
                  .replace(/\butc(?:[+-]\d{1,2}(?::\d{2})?)?\b/gi, ' ')
                  .replace(/\s*[|•·]+\s*/g, ' ')
                  .replace(/\s+/g, ' ')
                  .trim();
                const isNoise = (value) => !value || noisePatterns.some((pattern) => pattern.test(value));
                const isLikelyHeadline = (value) => {
                  if (!value || value.length < 3 || value.length > 120 || isNoise(value)) return false;
                  const normalized = value.toLowerCase();
                  if (metadataMarkers.some((marker) => normalized.includes(marker))) return false;
                  if (normalized.startsWith('./explore?') || normalized.startsWith('http://') || normalized.startsWith('https://')) return false;
                  if (value.includes(',') && value.split(',').length >= 3) return false;
                  return true;
                };
                const extractLineCandidate = (value) => {
                  for (const line of (value || '').split(/\r?\n+/)) {
                    const cleaned = clean(line);
                    if (isLikelyHeadline(cleaned)) return cleaned;
                  }
                  return '';
                };
                const pushCandidate = (output, candidate) => {
                  const cleaned = clean(candidate);
                  if (!isLikelyHeadline(cleaned)) return;
                  const slug = cleaned.toLowerCase();
                  if (seen.has(slug)) return;
                  seen.add(slug);
                  output.push(cleaned);
                };
                const rows = [];
                for (const selector of rowSelectors) {
                  const found = Array.from(document.querySelectorAll(selector)).filter(visible);
                  if (found.length) {
                    rows.push(...found);
                    break;
                  }
                }
                const output = [];
                for (const row of rows) {
                  let title = '';
                  for (const selector of titleSelectors) {
                    const el = Array.from(row.querySelectorAll(selector)).find(visible);
                    if (!el) continue;
                    title = el.getAttribute('data-term') || el.getAttribute('title') || el.innerText || '';
                    title = clean(title);
                    if (isLikelyHeadline(title)) break;
                    title = '';
                  }
                  if (!title) {
                    title = extractLineCandidate(row.innerText || row.textContent || '');
                  }
                  pushCandidate(output, title);
                  if (output.length >= maxTrends) return output;
                }
                if (output.length) return output;
                for (const selector of titleSelectors) {
                  const elements = Array.from(document.querySelectorAll(selector)).filter(visible);
                  for (const el of elements) {
                    pushCandidate(output, el.getAttribute('data-term') || el.getAttribute('title') || el.innerText || '');
                    if (output.length >= maxTrends) return output;
                  }
                }
                return output;
                """, desiredPositive(maxTrends));


        if (!(result instanceof Collection<?> collection)) {
            return List.of();
        }

        List<String> titles = new ArrayList<>();
        for (Object value : collection) {
            if (value instanceof String text && !text.isBlank()) {
                titles.add(text.trim());
            }
        }
        return titles;
    }

    private int desiredPositive(int maxTrends) {
        return Math.max(1, maxTrends);
    }

    RemoteWebDriver createDriver(String proxy) throws Exception {
        RemoteWebDriver driver = new RemoteWebDriver(new URL(properties.selenium().remoteUrl()), buildOptions(proxy));
        applyStealth(driver);
        return driver;
    }

    ChromeOptions buildOptions(String proxy) {
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        if (properties.selenium().headless()) {
            options.addArguments("--headless=new", "--window-size=1600,1200");
        } else {
            options.addArguments("--start-maximized");
        }
        options.addArguments(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-background-networking",
                "--disable-background-timer-throttling",
                "--disable-renderer-backgrounding",
                "--disable-features=Translate,OptimizationHints,MediaRouter",
                "--lang=en-US"
        );
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation", "enable-logging"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("prefs", Map.of(
                "intl.accept_languages", "en-US,en",
                "credentials_enable_service", false,
                "profile.password_manager_enabled", false
        ));
        String userAgent = properties.selenium().userAgent();
        if (userAgent != null && !userAgent.isBlank()) {
            options.addArguments("--user-agent=" + userAgent.trim());
        }
        if (proxy != null && !proxy.isBlank()) {
            options.addArguments("--proxy-server=http://" + proxy);
        }
        return options;
    }

    void applyStealth(RemoteWebDriver driver) {
        try {
            driver.get("data:,");
            executeCdpCommand(driver, "Page.addScriptToEvaluateOnNewDocument", Map.of("source", STEALTH_SCRIPT));
            if (driver instanceof JavascriptExecutor javascriptExecutor) {
                javascriptExecutor.executeScript(STEALTH_SCRIPT);
            }
        } catch (Exception exception) {
            log.debug("Unable to apply Selenium stealth script: {}", exception.getMessage());
        }
    }

    private void executeCdpCommand(RemoteWebDriver driver, String command, Map<String, Object> parameters) throws Exception {
        driver.getClass().getMethod("executeCdpCommand", String.class, Map.class).invoke(driver, command, parameters);
    }

    private void dismissConsent(WebDriver driver) {
        for (By selector : List.of(
                By.cssSelector("button#L2AGLb"),
                By.cssSelector("button[aria-label*='Accept']"),
                By.xpath("//button[contains(., 'Accept all') or contains(., 'I agree')]")
        )) {
            try {
                List<WebElement> buttons = driver.findElements(selector);
                if (!buttons.isEmpty()) {
                    buttons.get(0).click();
                    sleep(properties.selenium().interactionDelayMs());
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureNewsMode(WebDriver driver, String searchUrl) {
        try {
            if (!driver.findElements(By.cssSelector("div.SoaBEf, div.dbsr, div.MjjYud, article, g-card")).isEmpty()) {
                return;
            }
            List<WebElement> newsTabs = driver.findElements(By.cssSelector("a[href*='tbm=nws']"));
            if (!newsTabs.isEmpty()) {
                newsTabs.get(0).click();
                sleep(properties.selenium().interactionDelayMs());
                return;
            }
            if (!searchUrl.contains("tbm=nws")) {
                driver.get(searchUrl + (searchUrl.contains("?") ? "&" : "?") + "tbm=nws&udm=14");
                sleep(properties.selenium().interactionDelayMs());
            }
        } catch (Exception exception) {
            log.debug("Unable to force news mode: {}", exception.getMessage());
        }
    }

    private void performLightInteraction(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor javascriptExecutor)) {
            return;
        }
        try {
            javascriptExecutor.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.35);");
            sleep(properties.selenium().interactionDelayMs());
            javascriptExecutor.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.7);");
            sleep(properties.selenium().interactionDelayMs());
        } catch (Exception exception) {
            log.debug("Light interaction failed: {}", exception.getMessage());
        }
    }

    private boolean waitForManualVerificationIfNeeded(WebDriver driver, String trend) {
        if (!isLikelyBotCheckPage(driver)) {
            return true;
        }
        if (!properties.selenium().manualVerificationWaitEnabled()) {
            log.warn("Bot-check detected and manual verification is disabled.");
            return false;
        }
        if (properties.selenium().headless()) {
            log.warn("Manual verification wait is enabled but Selenium is headless. Set SELENIUM_HEADLESS=false to solve bot checks interactively.");
        }
        String noVncUrl = resolveNoVncUrl();
        String context = trend == null || trend.isBlank() ? "page" : "trend='" + trend + "'";
        if (noVncUrl != null) {
            log.warn("Google bot-check detected for {}. Complete verification in Selenium noVNC at {} within {} seconds.",
                    context,
                    noVncUrl,
                    properties.selenium().manualVerificationMaxWaitSeconds());
        } else {
            log.warn("Google bot-check detected for {}. Waiting up to {} seconds for manual verification.",
                    context,
                    properties.selenium().manualVerificationMaxWaitSeconds());
        }
        long deadline = System.currentTimeMillis() + properties.selenium().manualVerificationMaxWaitSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (!isLikelyBotCheckPage(driver)) {
                return true;
            }
            sleep(Math.max(1000, properties.selenium().interactionDelayMs()));
        }
        log.warn("Timed out while waiting for Selenium bot verification for {}.", context);
        return false;
    }

    String resolveNoVncUrl() {
        if (properties.selenium().headless()) {
            return null;
        }
        try {
            URL remote = new URL(properties.selenium().remoteUrl());
            String host = remote.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            return "http://" + host + ":7900/?autoconnect=1&resize=scale";
        } catch (Exception exception) {
            return null;
        }
    }

    boolean isLikelyBotCheckPage(WebDriver driver) {
        try {
            String title = safeLower(driver.getTitle());
            String body = safeLower(driver.getPageSource());
            boolean hasChallengeFrame = !driver.findElements(BOT_CHALLENGE_SELECTOR).isEmpty();
            boolean hasNewsCards = !driver.findElements(By.cssSelector("div.SoaBEf, div.dbsr, div.MjjYud, g-card, article, a.WlydOe")).isEmpty();
            return isLikelyBotCheck(title, body, hasChallengeFrame, hasNewsCards);
        } catch (Exception exception) {
            return false;
        }
    }

    boolean isLikelyBotCheck(String title, String body, boolean hasChallengeFrame, boolean hasNewsCards) {
        boolean challengeText = title.contains("unusual traffic")
                || title.contains("sorry")
                || body.contains("verify you are human")
                || body.contains("i'm not a robot")
                || body.contains("unusual traffic")
                || body.contains("complete the captcha")
                || body.contains("g-recaptcha");
        return (hasChallengeFrame || challengeText) && !hasNewsCards;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private List<String> sanitize(List<String> proxies) {
        if (proxies == null) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String proxy : proxies) {
            if (proxy != null && !proxy.isBlank()) {
                cleaned.add(proxy.trim());
            }
        }
        return cleaned;
    }

    private void sleep(long milliseconds) {
        long duration = Math.max(100, milliseconds);
        try {
            Thread.sleep(duration + random.nextInt(150));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
