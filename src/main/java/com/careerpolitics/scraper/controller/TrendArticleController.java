package com.careerpolitics.scraper.controller;

import com.careerpolitics.scraper.model.request.TrendArticleRequest;
import com.careerpolitics.scraper.model.response.TrendArticleResponse;
import com.careerpolitics.scraper.service.TrendArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/careerpolitics/content")
@RequiredArgsConstructor
@Tag(name = "Trend Content", description = "APIs to generate and publish AI articles from trends/news")
public class TrendArticleController {

    private final TrendArticleService trendArticleService;

    @PostMapping("/trends/article")
    @Operation(summary = "Fetch Google trends + news, generate AI article, and optionally publish")
    public ResponseEntity<TrendArticleResponse> createTrendingArticle(@Valid @RequestBody TrendArticleRequest request) {
        return ResponseEntity.ok(trendArticleService.createAndOptionallyPublish(request));
    }
}
