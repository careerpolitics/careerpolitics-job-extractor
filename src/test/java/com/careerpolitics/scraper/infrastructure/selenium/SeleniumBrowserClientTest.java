package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.config.TrendingProperties;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeleniumBrowserClientTest {

    @Test
    void buildOptionsDoesNotForceLegacyUserAgentWhenUnset() {
        SeleniumBrowserClient client = new SeleniumBrowserClient(properties(true, "http://192.168.0.100:4444/wd/hub", ""));

        var options = client.buildOptions(null);

        assertThat(options.asMap().get("args")).asList().noneMatch(argument -> String.valueOf(argument).startsWith("--user-agent="));
        assertThat(options.asMap().get("args")).asList().contains("--headless=new", "--window-size=1600,1200", "--disable-background-networking");
        assertThat(options.asMap()).containsEntry("pageLoadStrategy", "eager");
    }

    @Test
    void resolveNoVncUrlUsesRemoteHostForHeadfulSessions() {
        SeleniumBrowserClient client = new SeleniumBrowserClient(properties(false, "http://192.168.0.100:4444/wd/hub", ""));

        assertThat(client.resolveNoVncUrl()).isEqualTo("http://192.168.0.100:7900/?autoconnect=1&resize=scale");
    }

    @Test
    void buildOptionsHonorsExplicitUserAgentOverride() {
        SeleniumBrowserClient client = new SeleniumBrowserClient(properties(false, "http://192.168.0.100:4444/wd/hub", "custom-agent"));

        var options = client.buildOptions(null);

        assertThat(options.asMap().get("args")).asList().contains("--user-agent=custom-agent", "--start-maximized");
    }

    @Test
    void applyStealthFallsBackWithoutThrowingWhenCdpIsUnavailable() {
        SeleniumBrowserClient client = new SeleniumBrowserClient(properties(false, "http://192.168.0.100:4444/wd/hub", ""));
        RemoteWebDriver driver = org.mockito.Mockito.mock(RemoteWebDriver.class, org.mockito.Mockito.withSettings().extraInterfaces(org.openqa.selenium.JavascriptExecutor.class));

        client.applyStealth(driver);

        org.mockito.Mockito.verify(driver).get("data:,");
        org.mockito.Mockito.verify((org.openqa.selenium.JavascriptExecutor) driver).executeScript(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void isLikelyBotCheckIsFalseForNormalGoogleResultsPageText() {
        SeleniumBrowserClient client = new SeleniumBrowserClient(properties(false, "http://192.168.0.100:4444/wd/hub", ""));

        boolean detected = client.isLikelyBotCheck(
                "test - google search",
                "top stories example headline from example news more results",
                false,
                true
        );

        assertThat(detected).isFalse();
    }

    @Test
    void isLikelyBotCheckIsTrueForChallengeFrameWithoutNewsCards() {
        SeleniumBrowserClient client = new SeleniumBrowserClient(properties(false, "http://192.168.0.100:4444/wd/hub", ""));

        boolean detected = client.isLikelyBotCheck(
                "google search",
                "verify you are human",
                true,
                false
        );

        assertThat(detected).isTrue();
    }

    @Test
    void isLikelyBotCheckIsFalseWhenChallengeTextAppearsAlongsideNewsCards() {
        SeleniumBrowserClient client = new SeleniumBrowserClient(properties(false, "http://192.168.0.100:4444/wd/hub", ""));

        boolean detected = client.isLikelyBotCheck(
                "google search",
                "verify you are human g-recaptcha latest headlines",
                false,
                true
        );

        assertThat(detected).isFalse();
    }

    private TrendingProperties properties(boolean headless, String remoteUrl, String userAgent) {
        return new TrendingProperties(
                new TrendingProperties.Discovery("https://trends.google.com/trending", 5, List.of()),
                new TrendingProperties.News("https://www.google.com/search", 4),
                new TrendingProperties.Selenium(true, true, headless, true, 120, 20, 2, 750, remoteUrl, userAgent, List.of(), Duration.ofSeconds(2)),
                new TrendingProperties.Generation(false, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", null, Duration.ofSeconds(20)),
                new TrendingProperties.Publishing(false, null, null, null, Duration.ofSeconds(10)),
                new TrendingProperties.Scheduler(false, "0 0 */6 * * *", "US", "en-US", 3, 4, 24, false)
        );
    }
}
