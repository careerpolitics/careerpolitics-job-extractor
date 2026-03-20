package com.careerpolitics.scraper.infrastructure.detail;

import com.careerpolitics.scraper.domain.model.ArticleDetails;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.TrendHeadlineDetailClient;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class JsoupTrendHeadlineDetailClient implements TrendHeadlineDetailClient {

    private static final int REQUEST_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_CONTENT_LENGTH = 4_000;

    @Override
    public List<TrendHeadline> enrich(List<TrendHeadline> headlines) {
        if (headlines == null || headlines.isEmpty()) {
            return List.of();
        }

        List<TrendHeadline> enriched = new ArrayList<>(headlines.size());
        for (TrendHeadline headline : headlines) {
            enriched.add(enrichHeadline(headline));
        }
        return enriched;
    }

    TrendHeadline enrichHeadline(TrendHeadline headline) {
        if (headline == null || headline.link() == null || headline.link().isBlank()) {
            return headline;
        }
        try {
            Document document = Jsoup.connect(headline.link())
                    .userAgent("Mozilla/5.0")
                    .timeout(REQUEST_TIMEOUT_MILLIS)
                    .get();
            ArticleDetails details = extractDetails(document, headline);
            log.info("Enriched headline details for source='{}' url='{}' mediaType={}",
                    headline.source(),
                    headline.link(),
                    details.mediaType());
            return new TrendHeadline(
                    headline.trend(),
                    headline.title(),
                    headline.link(),
                    headline.source(),
                    headline.publishedAt(),
                    headline.summary(),
                    details
            );
        } catch (Exception exception) {
            log.warn("Unable to enrich headline details for url='{}': {}", headline.link(), exception.getMessage());
            return headline;
        }
    }

    ArticleDetails extractDetails(Document document, TrendHeadline headline) {
        String description = firstNonBlank(
                metaContent(document, "meta[property=og:description]"),
                metaContent(document, "meta[name=twitter:description]"),
                metaContent(document, "meta[name=description]"),
                headline.summary()
        );
        List<String> rawMediaCandidates = new ArrayList<>();
        rawMediaCandidates.add(metaContent(document, "meta[property=og:image]"));
        rawMediaCandidates.add(metaContent(document, "meta[name=twitter:image]"));
        rawMediaCandidates.add(metaContent(document, "meta[itemprop=image]"));
        rawMediaCandidates.add(metaContent(document, "meta[property=og:video]"));
        rawMediaCandidates.add(metaContent(document, "meta[property=og:video:url]"));
        rawMediaCandidates.add(iframeSource(document));
        if (headline.articleDetails() != null && headline.articleDetails().mediaUrls() != null) {
            rawMediaCandidates.addAll(headline.articleDetails().mediaUrls());
        }
        List<String> mediaUrls = sanitizeMediaUrls(rawMediaCandidates);
        String mediaType = inferMediaType(mediaUrls.isEmpty() ? null : mediaUrls.get(0), document);
        String content = extractContent(document);
        return new ArticleDetails(description, content, mediaUrls, mediaType);
    }

    private String metaContent(Document document, String selector) {
        Element meta = document.selectFirst(selector);
        if (meta == null) {
            return null;
        }
        String content = meta.absUrl("content");
        if (content != null && !content.isBlank()) {
            return content;
        }
        content = meta.attr("content");
        return content == null || content.isBlank() ? null : content.trim();
    }

    private String iframeSource(Document document) {
        Element iframe = document.selectFirst("iframe[src*='youtube.com'], iframe[src*='youtu.be'], iframe[src*='player.vimeo.com']");
        if (iframe == null) {
            return null;
        }
        String src = iframe.absUrl("src");
        if (src != null && !src.isBlank()) {
            return src;
        }
        src = iframe.attr("src");
        return src == null || src.isBlank() ? null : src.trim();
    }

    private String extractContent(Document document) {
        LinkedHashSet<String> paragraphs = new LinkedHashSet<>();
        for (Element paragraph : document.select("article p, main p, body p")) {
            String text = paragraph.text().trim();
            if (text.length() >= 40) {
                paragraphs.add(text);
            }
            if (paragraphs.size() >= 8) {
                break;
            }
        }
        String joined = String.join("\n\n", paragraphs);
        if (joined.length() <= MAX_CONTENT_LENGTH) {
            return joined;
        }
        return joined.substring(0, MAX_CONTENT_LENGTH) + "...";
    }

    private String inferMediaType(String mediaUrl, Document document) {
        String url = mediaUrl == null ? "" : mediaUrl.toLowerCase(Locale.ROOT);
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            return "youtube";
        }
        if (url.endsWith(".gif") || url.contains(".gif?")) {
            return "gif";
        }
        if (url.endsWith(".mp4") || url.contains(".mp4?") || document.selectFirst("meta[property=og:video], meta[property=og:video:url], video[src]") != null) {
            return "video";
        }
        if (!url.isBlank()) {
            return "image";
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private List<String> sanitizeMediaUrls(List<String> rawCandidates) {
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String rawCandidate : rawCandidates) {
            if (rawCandidate == null || rawCandidate.isBlank()) {
                continue;
            }
            for (String candidate : rawCandidate.split("\n")) {
                String trimmed = candidate == null ? null : candidate.trim();
                if (trimmed == null || trimmed.isBlank()) {
                    continue;
                }
                String lower = trimmed.toLowerCase(Locale.ROOT);
                if (lower.startsWith("data:")) {
                    continue;
                }
                sanitized.add(trimmed);
            }
        }
        return List.copyOf(sanitized);
    }
}
