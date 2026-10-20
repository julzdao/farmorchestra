package com.farmorchestra.api.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts business-rule errors into clean HTTP 400 responses.
 */
@RestControllerAdvice
public class ApiErrors {

    /**
     * Maps a rejected reading or query to HTTP 400, using the exception message as the detail.
     *
     * <p>Rule for contributors: only throw {@code IllegalArgumentException} with messages that are
     * safe to show to a client.
     *
     * @param error the validation failure from the service layer
     * @return a {@code 400 Bad Request} problem response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidReading(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }
}
