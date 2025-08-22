package com.careerpolitics.scraper.util;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HtmlParser {

    public Document parse(String html) {
        return Jsoup.parse(html);
    }

    public String selectText(Document doc, String cssQuery) {
        Element el = doc.selectFirst(cssQuery);
        return el != null ? el.text() : null;
    }

    public Map<String, String> selectKeyValue(Document doc, String keySelector, String valueSelector) {
        Map<String, String> map = new LinkedHashMap<>();
        Elements keys = doc.select(keySelector);
        Elements values = doc.select(valueSelector);
        int size = Math.min(keys.size(), values.size());
        for (int i = 0; i < size; i++) {
            map.put(keys.get(i).text(), values.get(i).text());
        }
        return map;
    }
}
