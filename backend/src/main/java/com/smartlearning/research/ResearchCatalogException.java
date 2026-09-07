package com.smartlearning.research;

import org.springframework.http.HttpStatus;

class ResearchCatalogException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    ResearchCatalogException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }
}
