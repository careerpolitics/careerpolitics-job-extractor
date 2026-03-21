package com.careerpolitics.scraper.application;

import com.careerpolitics.scraper.domain.model.ArticleDetails;
import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.PublishingResult;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.ArticleGenerator;
import com.careerpolitics.scraper.domain.port.ArticlePublisher;
import com.careerpolitics.scraper.domain.port.TrendDiscoveryClient;
import com.careerpolitics.scraper.domain.port.TrendHeadlineDetailClient;
import com.careerpolitics.scraper.domain.port.TrendNewsClient;
import com.careerpolitics.scraper.domain.request.TrendingArticleRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrendingWorkflowServiceTest {

    @Test
    void remembersTrendOnlyAfterSuccessfulPublish() {
        TrendDiscoveryClient trendDiscoveryClient = Mockito.mock(TrendDiscoveryClient.class);
        TrendNewsClient trendNewsClient = Mockito.mock(TrendNewsClient.class);
        TrendSelectionService trendSelectionService = Mockito.mock(TrendSelectionService.class);
        ArticleGenerator articleGenerator = Mockito.mock(ArticleGenerator.class);
        ArticlePublisher articlePublisher = Mockito.mock(ArticlePublisher.class);
        TrendHeadlineDetailClient trendHeadlineDetailClient = Mockito.mock(TrendHeadlineDetailClient.class);
        TrendingWorkflowService service = new TrendingWorkflowService(
                trendDiscoveryClient,
                trendNewsClient,
                trendSelectionService,
                articleGenerator,
                articlePublisher,
                trendHeadlineDetailClient
        );

        List<TrendHeadline> headlines = List.of(
                new TrendHeadline("AI Jobs", "Hiring expands", "https://example.com/story", "Reuters", null, "Summary",
                        new ArticleDetails("Summary", "Detailed content", List.of("https://example.com/image.jpg"), "image"))
        );
        when(trendDiscoveryClient.discover(anyString(), anyString(), anyInt())).thenReturn(List.of("AI Jobs"));
        when(trendSelectionService.pickFreshTrends(anyList(), anyInt(), anyInt())).thenReturn(List.of("AI Jobs"));
        when(trendNewsClient.discover(anyString(), anyString(), anyString(), anyInt())).thenReturn(headlines);
        when(trendHeadlineDetailClient.enrich(headlines)).thenReturn(headlines);
        when(articleGenerator.generate(anyString(), anyString(), anyList())).thenReturn(
                new GeneratedArticleDraft("Title", "Markdown", List.of("tag1"), "Description", "open-router")
        );
        when(articlePublisher.publish(anyString(), anyString(), anyString(), anyList(), anyString(), anyList(), any())).thenReturn(
                new PublishingResult(true, "Published successfully.", null)
        );

        TrendingArticleRequest request = new TrendingArticleRequest();
        request.setPublish(true);
        service.generate(request);

        verify(trendSelectionService).remember("AI Jobs", true);
    }

    @Test
    void doesNotRememberTrendWhenPublishingFailsOrIsSkipped() {
        TrendDiscoveryClient trendDiscoveryClient = Mockito.mock(TrendDiscoveryClient.class);
        TrendNewsClient trendNewsClient = Mockito.mock(TrendNewsClient.class);
        TrendSelectionService trendSelectionService = Mockito.mock(TrendSelectionService.class);
        ArticleGenerator articleGenerator = Mockito.mock(ArticleGenerator.class);
        ArticlePublisher articlePublisher = Mockito.mock(ArticlePublisher.class);
        TrendHeadlineDetailClient trendHeadlineDetailClient = Mockito.mock(TrendHeadlineDetailClient.class);
        TrendingWorkflowService service = new TrendingWorkflowService(
                trendDiscoveryClient,
                trendNewsClient,
                trendSelectionService,
                articleGenerator,
                articlePublisher,
                trendHeadlineDetailClient
        );

        when(trendDiscoveryClient.discover(anyString(), anyString(), anyInt())).thenReturn(List.of("AI Jobs"));
        when(trendSelectionService.pickFreshTrends(anyList(), anyInt(), anyInt())).thenReturn(List.of("AI Jobs"));
        when(trendNewsClient.discover(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(trendHeadlineDetailClient.enrich(List.of())).thenReturn(List.of());
        when(articleGenerator.generate(anyString(), anyString(), anyList())).thenReturn(
                new GeneratedArticleDraft("Title", "Markdown", List.of("tag1"), "Description", "open-router")
        );
        when(articlePublisher.publish(anyString(), anyString(), anyString(), anyList(), anyString(), anyList(), any())).thenReturn(
                new PublishingResult(false, "Publish failed.", null)
        );

        TrendingArticleRequest request = new TrendingArticleRequest();
        request.setPublish(true);
        service.generate(request);

        verify(trendSelectionService, never()).remember(anyString(), anyBoolean());
    }

    @Test
    void stillSendsArticleToPublisherWhenPublishFlagIsFalse() {
        TrendDiscoveryClient trendDiscoveryClient = Mockito.mock(TrendDiscoveryClient.class);
        TrendNewsClient trendNewsClient = Mockito.mock(TrendNewsClient.class);
        TrendSelectionService trendSelectionService = Mockito.mock(TrendSelectionService.class);
        ArticleGenerator articleGenerator = Mockito.mock(ArticleGenerator.class);
        ArticlePublisher articlePublisher = Mockito.mock(ArticlePublisher.class);
        TrendHeadlineDetailClient trendHeadlineDetailClient = Mockito.mock(TrendHeadlineDetailClient.class);
        TrendingWorkflowService service = new TrendingWorkflowService(
                trendDiscoveryClient,
                trendNewsClient,
                trendSelectionService,
                articleGenerator,
                articlePublisher,
                trendHeadlineDetailClient
        );

        List<TrendHeadline> headlines = List.of(
                new TrendHeadline("AI Jobs", "Hiring expands", "https://example.com/story", "Reuters", null, "Summary",
                        new ArticleDetails("Summary", "Detailed content", List.of("https://example.com/image.jpg"), "image"))
        );
        when(trendDiscoveryClient.discover(anyString(), anyString(), anyInt())).thenReturn(List.of("AI Jobs"));
        when(trendSelectionService.pickFreshTrends(anyList(), anyInt(), anyInt())).thenReturn(List.of("AI Jobs"));
        when(trendNewsClient.discover(anyString(), anyString(), anyString(), anyInt())).thenReturn(headlines);
        when(trendHeadlineDetailClient.enrich(headlines)).thenReturn(headlines);
        when(articleGenerator.generate(anyString(), anyString(), anyList())).thenReturn(
                new GeneratedArticleDraft("Title", "Markdown", List.of("tag1"), "Description", "open-router")
        );
        when(articlePublisher.publish(anyString(), anyString(), anyString(), anyList(), anyString(), anyList(), any())).thenReturn(
                new PublishingResult(true, "Stored as draft.", null)
        );

        TrendingArticleRequest request = new TrendingArticleRequest();
        request.setPublish(false);
        service.generate(request);

        verify(articlePublisher).publish(anyString(), anyString(), anyString(), anyList(), anyString(), anyList(), any());
        verify(trendSelectionService, never()).remember(anyString(), anyBoolean());
    }
}
