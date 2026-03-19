package com.careerpolitics.scraper.api;

import com.careerpolitics.scraper.application.TrendingWorkflowService;
import com.careerpolitics.scraper.domain.request.TrendingArticleRequest;
import com.careerpolitics.scraper.domain.response.TrendDiscoveryResponse;
import com.careerpolitics.scraper.domain.response.TrendNewsResponse;
import com.careerpolitics.scraper.domain.response.TrendingArticleResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trending")
public class TrendingController {

    private final TrendingWorkflowService workflowService;

    public TrendingController(TrendingWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/trends")
    public ResponseEntity<TrendDiscoveryResponse> discoverTrends(
            @RequestParam(defaultValue = "US") String geo,
            @RequestParam(defaultValue = "en-US") String language,
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
    public ResponseEntity<TrendNewsResponse> discoverNews(
            @RequestParam String trend,
            @RequestParam(defaultValue = "US") String geo,
            @RequestParam(defaultValue = "en-US") String language,
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
    public ResponseEntity<TrendingArticleResponse> generate(@Valid @RequestBody TrendingArticleRequest request) {
        return ResponseEntity.ok(workflowService.generate(request));
    }
}
