package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.config.TrendingProperties;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
public class SeleniumBrowserClient {

    private final TrendingProperties properties;
    private final Random random = new Random();

    public SeleniumBrowserClient(TrendingProperties properties) {
        this.properties = properties;
    }

    public String fetchTrendsPage(String url) {
        if (!properties.selenium().enabled()) {
            return "";
        }
        return load(url, false);
    }

    public String fetchNewsPage(String url, String trend) {
        if (!properties.selenium().enabled() || !properties.selenium().newsEnabled()) {
            return "";
        }
        return load(url, true);
    }

    private String load(String url, boolean newsMode) {
        if (properties.selenium().remoteUrl() == null || properties.selenium().remoteUrl().isBlank()) {
            log.warn("Selenium remote URL is not configured. Returning empty page source.");
            return "";
        }

        int attempts = Math.max(1, properties.selenium().maxAttempts());
        List<String> proxies = sanitize(properties.selenium().proxyPool());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            String proxy = proxies.isEmpty() ? null : proxies.get((attempt - 1) % proxies.size());
            WebDriver driver = null;
            try {
                driver = new RemoteWebDriver(new URL(properties.selenium().remoteUrl()), buildOptions(proxy));
                driver.get(url);

                Duration timeout = Duration.ofSeconds(properties.selenium().timeoutSeconds());
                new WebDriverWait(driver, timeout).until(d -> d.getPageSource() != null && d.getPageSource().contains("<body"));

                dismissConsent(driver);
                if (newsMode) {
                    ensureNewsMode(driver, url);
                }
                performLightInteraction(driver);
                if (!waitForBotCheck(driver)) {
                    continue;
                }

                return driver.getPageSource();
            } catch (SessionNotCreatedException exception) {
                log.warn("Selenium session creation failed on attempt {}: {}", attempt, exception.getMessage());
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
        return "";
    }

    ChromeOptions buildOptions(String proxy) {
        ChromeOptions options = new ChromeOptions();
        if (properties.selenium().headless()) {
            options.addArguments("--headless=new", "--window-size=1600,1200");
        } else {
            options.addArguments("--start-maximized");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--lang=en-US");
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

    private boolean waitForBotCheck(WebDriver driver) {
        if (!isBotCheckPage(driver)) {
            return true;
        }
        if (!properties.selenium().manualVerificationWaitEnabled()) {
            log.warn("Bot-check detected and manual verification is disabled.");
            return false;
        }
        String noVncUrl = resolveNoVncUrl();
        if (noVncUrl != null) {
            log.warn("Bot-check detected. Complete verification in Selenium noVNC at {} within {} seconds.",
                    noVncUrl,
                    properties.selenium().manualVerificationMaxWaitSeconds());
        }
        long deadline = System.currentTimeMillis() + properties.selenium().manualVerificationMaxWaitSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (!isBotCheckPage(driver)) {
                return true;
            }
            sleep(Math.max(1000, properties.selenium().interactionDelayMs()));
        }
        log.warn("Timed out while waiting for Selenium bot verification.");
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

    private boolean isBotCheckPage(WebDriver driver) {
        try {
            String title = String.valueOf(driver.getTitle()).toLowerCase(Locale.ROOT);
            String body = String.valueOf(driver.getPageSource()).toLowerCase(Locale.ROOT);
            boolean hasCaptcha = !driver.findElements(By.cssSelector(
                    "iframe[src*='captcha'], iframe[src*='sorry'], textarea[g-recaptcha-response], div.g-recaptcha, div.h-captcha"
            )).isEmpty();
            return hasCaptcha
                    || title.contains("unusual traffic")
                    || title.contains("captcha")
                    || body.contains("verify you are human")
                    || body.contains("our systems have detected unusual traffic")
                    || body.contains("i'm not a robot");
        } catch (Exception exception) {
            return false;
        }
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
