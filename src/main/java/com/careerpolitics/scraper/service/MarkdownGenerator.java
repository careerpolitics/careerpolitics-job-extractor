package com.careerpolitics.scraper.service;


import com.careerpolitics.scraper.model.JobDetail;
import org.springframework.stereotype.Service;

@Service
public class MarkdownGenerator {

    public String generate(JobDetail detail) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(detail.getTitle() != null ? detail.getTitle() : "Job Detail").append("\n\n");
        md.append("- **Source**: ").append(nullSafe(detail.getSourceWebsite())).append("\n");
        md.append("- **URL**: ").append(nullSafe(detail.getUrl())).append("\n");
        if (detail.getDepartment() != null) {
            md.append("- **Department**: ").append(detail.getDepartment()).append("\n");
        }
        if (detail.getVacancies() != null) {
            md.append("- **Vacancies**: ").append(detail.getVacancies()).append("\n");
        }
        if (detail.getApplicationFee() != null) {
            md.append("- **Application Fee**: ").append(detail.getApplicationFee()).append("\n");
        }
        md.append("\n");
        if (detail.getDescription() != null) {
            md.append("## Description\n\n").append(detail.getDescription()).append("\n\n");
        }
        if (detail.getImportantDates() != null && !detail.getImportantDates().isEmpty()) {
            md.append("## Important Dates\n\n");
            detail.getImportantDates().forEach((k, v) -> md.append("- ").append(k).append(": ").append(v).append("\n"));
            md.append("\n");
        }
        if (detail.getEligibilityCriteria() != null && !detail.getEligibilityCriteria().isEmpty()) {
            md.append("## Eligibility\n\n");
            detail.getEligibilityCriteria().forEach(c -> md.append("- ").append(c).append("\n"));
            md.append("\n");
        }
        if (detail.getSelectionProcess() != null) {
            md.append("## Selection Process\n\n").append(detail.getSelectionProcess()).append("\n\n");
        }
        if (detail.getExamPattern() != null) {
            md.append("## Exam Pattern\n\n").append(detail.getExamPattern()).append("\n\n");
        }
        return md.toString();
    }

    private String nullSafe(String in) {
        return in == null ? "" : in;
    }
}
