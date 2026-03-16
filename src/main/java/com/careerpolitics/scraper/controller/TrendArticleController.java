package com.careerpolitics.scraper.controller;

import com.careerpolitics.scraper.model.request.TrendArticleRequest;
import com.careerpolitics.scraper.model.response.TrendArticleResponse;
import com.careerpolitics.scraper.model.response.TrendDiscoveryResponse;
import com.careerpolitics.scraper.model.response.TrendMediaResponse;
import com.careerpolitics.scraper.model.response.TrendNewsItem;
import com.careerpolitics.scraper.model.response.TrendNewsResponse;
import com.careerpolitics.scraper.service.TrendArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/careerpolitics/content")
@RequiredArgsConstructor
@Tag(name = "Trend Content", description = "APIs to generate and publish AI articles from trends/news")
public class TrendArticleController {

    private final TrendArticleService trendArticleService;


    @GetMapping("/trends/discover")
    @Operation(summary = "Discover current trend topics", description = "Returns trend keywords from Google Trends (or fallback list when provided).")
    public ResponseEntity<TrendDiscoveryResponse> discoverTrends(
            @RequestParam(value = "geo", defaultValue = "IN") String geo,
            @RequestParam(value = "language", defaultValue = "en-US") String language,
            @RequestParam(value = "maxTrends", defaultValue = "5") int maxTrends
    ) {
        var trends = trendArticleService.discoverTrends(geo, language, maxTrends, null);
        return ResponseEntity.ok(TrendDiscoveryResponse.builder()
                .geo(geo)
                .language(language)
                .maxTrends(maxTrends)
                .trends(trends)
                .build());
    }

    @GetMapping("/trends/news")
    @Operation(summary = "Fetch news for a single trend", description = "Executes only the news discovery step for one trend keyword.")
    public ResponseEntity<TrendNewsResponse> discoverTrendNews(
            @RequestParam("trend") String trend,
            @RequestParam(value = "geo", defaultValue = "IN") String geo,
            @RequestParam(value = "language", defaultValue = "en-US") String language,
            @RequestParam(value = "maxNewsPerTrend", defaultValue = "3") int maxNewsPerTrend
    ) {
        var news = trendArticleService.discoverNewsForTrend(trend, maxNewsPerTrend, geo, language);
        return ResponseEntity.ok(TrendNewsResponse.builder()
                .trend(trend)
                .geo(geo)
                .language(language)
                .maxNewsPerTrend(maxNewsPerTrend)
                .news(news)
                .build());
    }

    @GetMapping("/trends/media")
    @Operation(summary = "Fetch media for a single trend", description = "Runs news + media extraction steps and returns curated media items with cover image.")
    public ResponseEntity<TrendMediaResponse> discoverTrendMedia(
            @RequestParam("trend") String trend,
            @RequestParam(value = "geo", defaultValue = "IN") String geo,
            @RequestParam(value = "language", defaultValue = "en-US") String language,
            @RequestParam(value = "maxNewsPerTrend", defaultValue = "3") int maxNewsPerTrend,
            @RequestParam(value = "maxMediaItems", defaultValue = "8") int maxMediaItems
    ) {
        java.util.List<TrendNewsItem> newsItems = trendArticleService.discoverNewsForTrend(trend, maxNewsPerTrend, geo, language);
        var mediaBundle = trendArticleService.discoverMediaForTrend(trend, newsItems, geo, language, maxMediaItems);

        return ResponseEntity.ok(TrendMediaResponse.builder()
                .trend(trend)
                .geo(geo)
                .language(language)
                .newsItemsConsidered(newsItems.size())
                .maxMediaItems(maxMediaItems)
                .coverImage(mediaBundle.coverImage())
                .media(mediaBundle.items())
                .build());
    }

    @PostMapping("/trends/article")
    @Operation(summary = "Fetch Google trends + news, generate AI article, and optionally publish",
            description = "Builds a Forem-ready article from trends and related headlines; optionally publishes to CareerPolitics article API.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "404", description = "No trend/news data found"),
            @ApiResponse(responseCode = "500", description = "Generation or publishing failed")
    })
    public ResponseEntity<TrendArticleResponse> createTrendingArticle(@Valid @RequestBody TrendArticleRequest request) {
        log.info("Trend article request received: geo={}, language={}, maxTrends={}, maxNewsPerTrend={}, publish={}",
                request.getGeo(), request.getLanguage(), request.getMaxTrends(), request.getMaxNewsPerTrend(), request.shouldPublish());

        TrendArticleResponse response = trendArticleService.createAndOptionallyPublish(request);
        log.info("Trend article request completed: trends={}, generatedArticles={}",
                response.getTrends() != null ? response.getTrends().size() : 0,
                response.getArticles() != null ? response.getArticles().size() : 0);
        return ResponseEntity.ok(response);
    }
}
