package com.leadlens.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.NoSuchElementException;

/** Errors come back as {"error": "..."} with a message a user can act on. */
@RestControllerAdvice
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** Bad JSON, or a thesis whose constructor rejected it (e.g. minimum above maximum). */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        String msg = root instanceof IllegalArgumentException ? root.getMessage() : "The request body is not valid JSON for this endpoint.";
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    /** An upstream we call (webhook target) failed; the message says which and how. */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> upstream(IllegalStateException e) {
        log.warn("Upstream failure: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "The file is larger than 20 MB."));
    }

    @ExceptionHandler(java.io.IOException.class)
    ResponseEntity<Map<String, String>> io(java.io.IOException e) {
        log.warn("I/O error: {}", e.toString());
        return ResponseEntity.badRequest().body(Map.of("error", "Couldn't read the file: " + e.getMessage()));
    }
}
