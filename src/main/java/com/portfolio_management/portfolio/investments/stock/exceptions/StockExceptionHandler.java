package com.portfolio_management.portfolio.investments.stock.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.portfolio_management.portfolio.investments.stock")
public class StockExceptionHandler {

    @ExceptionHandler(StockModuleException.class)
    public ResponseEntity<ApiErrorResponse> handleStockException(StockModuleException ex) {
        HttpStatus status = ex.getStatus();
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(Instant.now(), 400, "Bad Request", "Validation failed", fields));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(Instant.now(), 500, "Internal Server Error", ex.getMessage(), null));
    }
}

