package com.careerpolitics.scraper.util;


import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ContentCleaner {

    public String normalizeWhitespace(String input) {
        if (input == null) return null;
        String withoutNonBreaking = input.replace('\u00A0', ' ');
        String collapsed = withoutNonBreaking.replaceAll("\r?\n", "\n");
        collapsed = collapsed.replaceAll("\n{3,}", "\n\n");
        return StringUtils.normalizeSpace(collapsed);
    }

    public String removeScriptsAndStyles(String html) {
        if (html == null) return null;
        return html.replaceAll("<script[\\s\\S]*?</script>", "")
                .replaceAll("<style[\\s\\S]*?</style>", "");
    }

    public String stripHtmlTags(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", "");
    }
}
