package com.careerpolitics.scraper.controller;

import com.careerpolitics.scraper.model.request.TrendArticleRequest;
import com.careerpolitics.scraper.model.response.TrendArticleResponse;
import com.careerpolitics.scraper.service.TrendArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/careerpolitics/content")
@RequiredArgsConstructor
@Tag(name = "Trend Content", description = "APIs to generate and publish AI articles from trends/news")
public class TrendArticleController {

    private final TrendArticleService trendArticleService;

    @PostMapping("/trends/article")
    @Operation(summary = "Fetch Google trends + news, generate AI article, and optionally publish",
            description = "Builds a Forem-ready article from trends and related headlines; optionally publishes to CareerPolitics article API.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "500", description = "Generation or publishing failed")
    })
    public ResponseEntity<TrendArticleResponse> createTrendingArticle(@Valid @RequestBody TrendArticleRequest request) {
        log.info("Trend article request received: geo={}, language={}, maxTrends={}, maxNewsPerTrend={}, publish={}",
                request.getGeo(), request.getLanguage(), request.getMaxTrends(), request.getMaxNewsPerTrend(), request.isPublish());

        TrendArticleResponse response = trendArticleService.createAndOptionallyPublish(request);
        log.info("Trend article request completed: trends={}, generatedArticles={}",
                response.getTrends() != null ? response.getTrends().size() : 0,
                response.getArticles() != null ? response.getArticles().size() : 0);
        return ResponseEntity.ok(response);
    }
}
