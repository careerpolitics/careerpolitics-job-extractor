package com.careerpolitics.scraper.service;


import com.careerpolitics.scraper.model.JobDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiMarkdownService {

    private final AiContentService aiContentService;

    public String generateRichMarkdown(JobDetail d) {
        String prompt = buildPrompt(d);
        String result = aiContentService.generate(prompt);
        if (result == null || result.isBlank()) {
            return fallback(d);
        }
        return result.trim();
    }

    private String buildPrompt(JobDetail d) {
        StringBuilder p = new StringBuilder();
        p.append("Create production-ready, rich Markdown for a government job post using this structure and style.\n");
        p.append("Follow exactly this format with headings, tables, emojis, and bold labels similar to the example.\n");
        p.append("Do NOT add explanations. Output Markdown only.\n\n");
        p.append("Fields:\n");
        p.append("- title: ").append(n(d.getTitle())).append("\n");
        p.append("- department: ").append(n(d.getDepartment())).append("\n");
        p.append("- vacancies: ").append(d.getVacancies() == null ? "N/A" : d.getVacancies()).append("\n");
        p.append("- applicationLink: ").append(n(d.getApplicationLink())).append("\n");
        p.append("- notificationLink: ").append(n(d.getNotificationLink())).append("\n");
        p.append("- sourceWebsite: ").append(n(d.getSourceWebsite())).append("\n");
        p.append("- description: ").append(n(d.getDescription())).append("\n");
        p.append("- importantDates: ").append(n(mapToLines(d.getImportantDates()))).append("\n");
        p.append("- eligibilityCriteria: ").append(n(listToLines(d.getEligibilityCriteria()))).append("\n");
        p.append("- examPattern: ").append(n(d.getExamPattern())).append("\n");
        p.append("- applicationFee: ").append(n(d.getApplicationFee())).append("\n");
        p.append("- selectionProcess: ").append(n(d.getSelectionProcess())).append("\n\n");
        p.append("Use this example style as reference for sections, tables, and formatting (adapt content to fields above):\n\n");
        p.append("> 📢 **Organization**: Border Security Force (BSF)  \n");
        p.append("> 🗓️ **Apply Window**: 25 July 2025 – 25 August 2025  \n");
        p.append("> 🌐 **Official Website**: [rectt.bsf.gov.in](https://rectt.bsf.gov.in/)  \n");
        p.append("> 📄 **Total Posts**: 3,588 (3,406 Male / 182 Female)\n\n");
        p.append("---\n\n");
        p.append("## 📎 Useful Links\n\n");
        p.append("| Link Type                     | URL |\n");
        p.append("|------------------------------|-----|\n");
        p.append("| 📝 **Apply Online**             | [Click to Apply](https://example.com/apply) |\n");
        p.append("| 📄 **Short Notification (PDF)** | [View PDF](https://example.com/short.pdf) |\n");
        p.append("| 📜 **Full Notification (PDF)**  | [Download PDF](https://example.com/full.pdf) |\n");
        p.append("| 🌐 **Official Site**            | [example.com](https://example.com/) |\n\n");
        p.append("---\n\n");
        p.append("## 🗓️ Important Dates\n\n");
        p.append("- **Start of Online Application**: 25 July 2025  \n");
        p.append("- **Last Date to Apply Online**: 25 August 2025  \n\n");
        p.append("---\n\n");
        p.append("## 💰 Application Fee\n\n");
        p.append("| Category            | Fee     |\n|---------------------|--------:|\n| General / OBC / EWS | ₹100/-  |\n| SC / ST / Female    | ₹0/-    |\n\n");
        p.append("---\n\n");
        p.append("## 🎓 Eligibility Criteria\n\n");
        p.append("(Fill from eligibilityCriteria and description. Include age limits if available.)\n\n");
        p.append("---\n\n");
        p.append("## 🧪 Selection Process\n\n");
        p.append("(Fill from selectionProcess)\n\n");
        p.append("---\n\n");
        p.append("## 📋 Exam Pattern\n\n");
        p.append("(Fill from examPattern; provide a table if available)\n\n");
        p.append("---\n\n");
        p.append("## 🧑‍💻 How to Apply\n\n");
        p.append("(Write clear numbered steps; use applicationLink if present)\n\n");
        p.append("---\n\n");
        p.append("## ❓ Frequently Asked Questions (FAQs)\n\n");
        p.append("(Generate 4-5 relevant Q&A)\n\n");
        return p.toString();
    }

    private String fallback(JobDetail d) {
        return "# " + n(d.getTitle());
    }

    private String n(String s) { return s == null ? "" : s; }
    private String mapToLines(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        map.forEach((k, v) -> b.append(k).append(": ").append(v).append("; "));
        return b.toString();
    }
    private String listToLines(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(", ", list);
    }
}