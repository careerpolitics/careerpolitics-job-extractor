package com.careerpolitics.scraper.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class WorkflowStepException extends RuntimeException {
    private final HttpStatus status;
    private final List<String> details;

    public WorkflowStepException(HttpStatus status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = details;
    }
}
