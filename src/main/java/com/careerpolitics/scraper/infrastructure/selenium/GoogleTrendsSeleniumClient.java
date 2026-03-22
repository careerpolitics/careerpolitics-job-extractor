package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.port.TrendDiscoveryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class GoogleTrendsSeleniumClient implements TrendDiscoveryClient {

    private static final List<String> INVALID_TOKENS = List.of(
            "google trends", "trending now", "home", "explore", "year in search", "go back", "searches",
            "trending_up", "active"
    );

    private static final String TRENDS_HEADER = "trends";
    private static final String TREND_BREAKDOWN_HEADER = "trend breakdown";

    private final SeleniumBrowserClient browserClient;
    private final TrendingProperties properties;
    private final TrendNormalizer trendNormalizer;

    public GoogleTrendsSeleniumClient(SeleniumBrowserClient browserClient,
                                      TrendingProperties properties,
                                      TrendNormalizer trendNormalizer) {
        this.browserClient = browserClient;
        this.properties = properties;
        this.trendNormalizer = trendNormalizer;
    }

    @Override
    public List<String> discover(String geo, String language, int maxTrends) {
        String url = properties.discovery().googleTrendsUrl()
                + "?geo=" + encode(geo)
                + "&hl=" + encode(language)
                + "&category=9&status=active";

        String csv = browserClient.fetchTrendCsv(url);
        List<String> trends = parseCsv(csv, maxTrends);
        if (!trends.isEmpty()) {
            log.info("Discovered {} trends using Selenium export CSV for geo={} language={}", trends.size(), geo, language);
            return trends;
        }

        trends = browserClient.fetchTrendTitles(url, maxTrends);
        if (!trends.isEmpty()) {
            log.warn("Falling back to DOM-based trend extraction after CSV export returned no trends for geo={} language={}", geo, language);
            return trends;
        }

        return properties.discovery().fallbackTrends() == null ? List.of() : properties.discovery().fallbackTrends();
    }

    List<String> parseCsv(String csv, int maxTrends) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }

        List<List<String>> rows = parseCsvRows(csv);
        if (rows.size() < 2) {
            return List.of();
        }

        Map<String, Integer> headerIndexes = headerIndexes(rows.getFirst());
        Integer trendsColumn = headerIndexes.get(TRENDS_HEADER);
        Integer breakdownColumn = headerIndexes.get(TREND_BREAKDOWN_HEADER);
        if (trendsColumn == null) {
            return List.of();
        }

        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            addIfValid(unique, cell(row, trendsColumn));
            for (String breakdownTerm : splitBreakdownTerms(cell(row, breakdownColumn))) {
                addIfValid(unique, breakdownTerm);
            }
        }

        return unique.values().stream().limit(Math.max(1, maxTrends)).toList();
    }

    private Map<String, Integer> headerIndexes(List<String> headerRow) {
        LinkedHashMap<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < headerRow.size(); index++) {
            indexes.put(trendNormalizer.clean(headerRow.get(index)).toLowerCase(Locale.ROOT), index);
        }
        return indexes;
    }

    private String cell(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index);
    }

    private List<String> splitBreakdownTerms(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<String> terms = new ArrayList<>();
        for (String item : raw.split("\\s*,\\s*")) {
            String cleaned = normalizeCandidate(item);
            if (!cleaned.isBlank()) {
                terms.add(cleaned);
            }
        }
        return terms;
    }

    private void addIfValid(LinkedHashMap<String, String> unique, String candidate) {
        String normalized = normalizeCandidate(candidate);
        if (isValidTrend(normalized)) {
            unique.putIfAbsent(trendNormalizer.slug(normalized), normalized);
        }
    }

    private boolean isValidTrend(String value) {
        if (value.isBlank() || value.length() < 3 || value.length() > 120) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return INVALID_TOKENS.stream().noneMatch(normalized::contains);
    }

    private String normalizeCandidate(String raw) {
        return trendNormalizer.clean(raw)
                .replace("\"\"", "\"")
                .replaceAll("^\"|\"$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<List<String>> parseCsvRows(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean insideQuotes = false;

        for (int index = 0; index < csv.length(); index++) {
            char current = csv.charAt(index);

            if (current == '"') {
                if (insideQuotes && index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                    currentCell.append('"');
                    index++;
                } else {
                    insideQuotes = !insideQuotes;
                }
                continue;
            }

            if (current == ',' && !insideQuotes) {
                currentRow.add(currentCell.toString());
                currentCell.setLength(0);
                continue;
            }

            if ((current == '\n' || current == '\r') && !insideQuotes) {
                if (current == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') {
                    index++;
                }
                currentRow.add(currentCell.toString());
                currentCell.setLength(0);
                if (!currentRow.stream().allMatch(String::isBlank)) {
                    rows.add(List.copyOf(currentRow));
                }
                currentRow = new ArrayList<>();
                continue;
            }

            currentCell.append(current);
        }

        currentRow.add(currentCell.toString());
        if (!currentRow.stream().allMatch(String::isBlank)) {
            rows.add(List.copyOf(currentRow));
        }

        return rows;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
