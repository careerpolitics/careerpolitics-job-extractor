package com.careerpolitics.scraper.api;

import com.careerpolitics.scraper.application.TrendingWorkflowService;
import com.careerpolitics.scraper.domain.request.TrendingArticleRequest;
import com.careerpolitics.scraper.domain.response.TrendDiscoveryResponse;
import com.careerpolitics.scraper.domain.response.TrendNewsResponse;
import com.careerpolitics.scraper.domain.response.TrendingArticleResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trending")
@Tag(name = "Trending API", description = "Discover Selenium-backed trends/news and generate trending articles.")
public class TrendingController {

    private final TrendingWorkflowService workflowService;

    public TrendingController(TrendingWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/trends")
    @Operation(summary = "Discover current trends", description = "Loads Google Trends via Selenium and returns normalized trend terms.")
    public ResponseEntity<TrendDiscoveryResponse> discoverTrends(
            @RequestParam(defaultValue = "IN") String geo,
            @RequestParam(defaultValue = "en-IN") String language,
            @RequestParam(defaultValue = "5") int maxTrends
    ) {
        return ResponseEntity.ok(new TrendDiscoveryResponse(
                geo,
                language,
                maxTrends,
                workflowService.discoverTrends(geo, language, maxTrends, null)
        ));
    }

    @GetMapping("/news")
    @Operation(summary = "Discover trend news", description = "Loads Google Search news results via Selenium for a single trend term.")
    public ResponseEntity<TrendNewsResponse> discoverNews(
            @RequestParam String trend,
            @RequestParam(defaultValue = "IN") String geo,
            @RequestParam(defaultValue = "en-IN") String language,
            @RequestParam(defaultValue = "4") int maxNewsPerTrend
    ) {
        return ResponseEntity.ok(new TrendNewsResponse(
                trend,
                geo,
                language,
                maxNewsPerTrend,
                workflowService.discoverNews(trend, geo, language, maxNewsPerTrend)
        ));
    }

    @PostMapping("/articles")
    @Operation(summary = "Generate trending articles", description = "Runs the full trending workflow and optionally publishes the generated articles.")
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "geo": "IN",
                              "language": "en-IN",
                              "maxTrends": 5,
                              "maxNewsPerTrend": 5,
                              "trendCooldownHours": 48,
                              "publish": false,
                              "requestedTrends": [
                                "some-trends"
                              ],
                              "articleApiToken": "token_key",
                              "organization_id": 0
                            }
                            """)
            )
    )
    public ResponseEntity<TrendingArticleResponse> generate(@Valid @org.springframework.web.bind.annotation.RequestBody TrendingArticleRequest request) {
        return ResponseEntity.ok(workflowService.generate(request));
    }
}
