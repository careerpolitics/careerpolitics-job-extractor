package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleTrendsSeleniumClientTest {

    @Test
    void parseCsvReturnsTrendAndBreakdownTermsInOrder() {
        GoogleTrendsSeleniumClient client = new GoogleTrendsSeleniumClient(null, properties(), new TrendNormalizer());

        String csv = """
                "Trends","Search volume","Started","Ended","Trend breakdown","Explore link"
                "jee mains session 2","20K+","March 22, 2026 at 12:30:00 AM UTC+5:30",,"jee mains session 2,nta,jee main 2026 city slip,nta jee,jee mains city intimation,jeemain.nta.nic.in 2026","./explore?q=jee%20mains%20session%202&geo=IN"
                "bihar board","2K+","March 22, 2026 at 7:30:00 AM UTC+5:30","March 22, 2026 at 3:00:00 PM UTC+5:30","bihar board,12th result 2026,inter ka result kab aaega,bihar board 10th result 2026,bseb 12th result 2026","./explore?q=bihar%20board&geo=IN"
                """;

        assertEquals(
                List.of(
                        "jee mains session 2",
                        "nta",
                        "jee main 2026 city slip",
                        "nta jee",
                        "jee mains city intimation",
                        "jeemain.nta.nic.in 2026",
                        "bihar board",
                        "12th result 2026",
                        "inter ka result kab aaega",
                        "bihar board 10th result 2026",
                        "bseb 12th result 2026"
                ),
                client.parseCsv(csv, 20)
        );
    }

    @Test
    void parseCsvHonorsMaxTrendsAcrossTrendAndBreakdownTerms() {
        GoogleTrendsSeleniumClient client = new GoogleTrendsSeleniumClient(null, properties(), new TrendNormalizer());

        String csv = """
                "Trends","Search volume","Started","Ended","Trend breakdown","Explore link"
                "vmou","100+","March 22, 2026 at 8:20:00 AM UTC+5:30","March 22, 2026 at 9:00:00 AM UTC+5:30","vmou,vmou result,vmou admit card","./explore?q=vmou&geo=IN"
                """;

        assertEquals(List.of("vmou", "vmou result"), client.parseCsv(csv, 2));
    }

    private TrendingProperties properties() {
        return new TrendingProperties(
                new TrendingProperties.Discovery("https://trends.google.com/trending", 5, List.of()),
                new TrendingProperties.News("https://www.google.com/search", 4),
                new TrendingProperties.Selenium(true, true, true, false, 45, 20, 2, 750, "http://selenium:4444/wd/hub", "", List.of(), Duration.ofSeconds(2)),
                new TrendingProperties.Generation(false, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "", Duration.ofSeconds(20)),
                new TrendingProperties.Publishing(false, "", "", null, Duration.ofSeconds(10)),
                new TrendingProperties.Scheduler(false, "0 0 */6 * * *", "US", "en-US", 3, 4, 24, false)
        );
    }
}
