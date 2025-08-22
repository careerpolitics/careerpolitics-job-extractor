package com.careerpolitics.scraper.service;


import com.careerpolitics.scraper.model.JobDetail;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MarkdownGenerator {

    public String generate(JobDetail detail) {
        StringBuilder md = new StringBuilder();
        // Title and hash tags
        md.append("🚆 ").append(nullSafe(detail.getTitle())).append("\n");
        md.append("#\n");
        md.append("# sarkarinaukri\n# governmentjobs\n# applyonline\n");
        md.append("\n");

        // Meta banner if available
        if (detail.getBannerImageUrl() != null) {
            md.append("![Banner](").append(detail.getBannerImageUrl()).append(")\n\n");
        }

        // Short intro
        md.append(nullSafe(detail.getDescription())).append("\n\n");

        // Important Dates
        if (detail.getImportantDates() != null && !detail.getImportantDates().isEmpty()) {
            md.append("🗓️ Important Dates\n");
            md.append("Event\tDate\n");
            for (Map.Entry<String, String> e : detail.getImportantDates().entrySet()) {
                md.append(e.getKey()).append("\t").append(e.getValue()).append("\n");
            }
            md.append("\n");
        }

        // Vacancy & Pay
        if (detail.getVacancies() != null) {
            md.append("📄 Vacancy & Pay Details\n");
            md.append("Total Posts: ").append(detail.getVacancies()).append("\n\n");
        }

        // Eligibility
        if (detail.getEligibilityCriteria() != null && !detail.getEligibilityCriteria().isEmpty()) {
            md.append("🎓 Eligibility Criteria\n");
            for (String c : detail.getEligibilityCriteria()) {
                md.append("- ").append(c).append("\n");
            }
            md.append("\n");
        }

        // Selection Process
        if (detail.getSelectionProcess() != null) {
            md.append("🧪 Selection Process\n").append(detail.getSelectionProcess()).append("\n\n");
        }

        // Exam Pattern
        if (detail.getExamPattern() != null) {
            md.append("📋 Exam Pattern\n").append(detail.getExamPattern()).append("\n\n");
        }

        // Application Fee
        if (detail.getApplicationFee() != null) {
            md.append("💰 Application Fee\n").append(detail.getApplicationFee()).append("\n\n");
        }

        // Links
        md.append("📎 Official Links\n");
        if (detail.getApplicationLink() != null) {
            md.append("📝 Apply Online\t").append(detail.getApplicationLink()).append("\n");
        }
        if (detail.getNotificationLink() != null) {
            md.append("📄 Notification (PDF)\t").append(detail.getNotificationLink()).append("\n");
        }
        md.append("\n");

        // FAQs (placeholder for SEO)
        md.append("❓ Frequently Asked Questions (FAQs)\n");
        md.append("Q1. What is the last date to apply?\n\n");
        md.append("Q2. How many vacancies are there?\n\n");
        md.append("Q3. What is the eligibility?\n\n");

        // Voice Search Keywords
        md.append("🔍 Voice Search Keywords\n");
        md.append("\"How to apply online for this recruitment?\"\n");
        md.append("\"Eligibility for technician posts\"\n");
        md.append("\"Age limit for government technician recruitment\"\n");
        md.append("\n");

        // CTA
        md.append("🎯 Stay updated on Government Jobs, Sarkari Results, and Exam Dates.\n");
        md.append("👉 WhatsApp Channel\n\n📢 Telegram Channel\n");

        return md.toString();
    }

    private String nullSafe(String in) {
        return in == null ? "" : in;
    }
}
