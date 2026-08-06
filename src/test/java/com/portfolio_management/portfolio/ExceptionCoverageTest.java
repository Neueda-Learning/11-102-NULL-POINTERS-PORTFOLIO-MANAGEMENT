package com.portfolio_management.portfolio;

import com.portfolio_management.portfolio.investments.crypto.exception.CryptoNotFoundException;
import com.portfolio_management.portfolio.investments.crypto.exception.GlobalExceptionHandler;
import com.portfolio_management.portfolio.investments.stock.exceptions.ApiErrorResponse;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockExceptionHandler;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers all exception classes and exception handlers.
 */
class ExceptionCoverageTest {

    // ── StockModuleException ─────────────────────────────────────────────────

    @Test
    void stockModuleException_messageAndStatus() {
        StockModuleException ex = new StockModuleException(HttpStatus.NOT_FOUND, "stock not found");
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("stock not found", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }

    // ── ApiErrorResponse (record) ────────────────────────────────────────────

    @Test
    void apiErrorResponse_record() {
        Instant now = Instant.now();
        ApiErrorResponse r = new ApiErrorResponse(now, 404, "Not Found", "missing", null);
        assertEquals(now, r.timestamp());
        assertEquals(404, r.status());
        assertEquals("Not Found", r.error());

        assertEquals("missing", r.message());
        assertNull(r.validationErrors());
    }

    @Test
    void apiErrorResponse_withValidationErrors() {
        ApiErrorResponse r = new ApiErrorResponse(Instant.now(), 400, "Bad Request", "Validation failed",
                Map.of("symbol", "must not be blank"));
        assertEquals(1, r.validationErrors().size());
        assertEquals("must not be blank", r.validationErrors().get("symbol"));
    }

    // ── StockExceptionHandler ────────────────────────────────────────────────

    @Test
    void stockExceptionHandler_handleStockException() {
        StockExceptionHandler handler = new StockExceptionHandler();
        StockModuleException ex = new StockModuleException(HttpStatus.NOT_FOUND, "not found");

        ResponseEntity<ApiErrorResponse> response = handler.handleStockException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("not found", response.getBody().message());
    }

    @Test
    void stockExceptionHandler_handleUnexpected() {
        StockExceptionHandler handler = new StockExceptionHandler();
        Exception ex = new RuntimeException("unexpected error");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("unexpected error", response.getBody().message());
    }

    @Test
    void stockExceptionHandler_handleValidationException() throws Exception {
        StockExceptionHandler handler = new StockExceptionHandler();

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "symbol", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertNotNull(response.getBody().validationErrors());
        assertEquals("must not be blank", response.getBody().validationErrors().get("symbol"));
    }

    // ── CryptoNotFoundException ──────────────────────────────────────────────

    @Test
    void cryptoNotFoundException_messageConstructor() {
        CryptoNotFoundException ex = new CryptoNotFoundException("crypto not found");
        assertEquals("crypto not found", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void cryptoNotFoundException_messageCauseConstructor() {
        Throwable cause = new IllegalArgumentException("bad id");
        CryptoNotFoundException ex = new CryptoNotFoundException("crypto error", cause);
        assertEquals("crypto error", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void cryptoNotFoundException_symbolIdConstructor() {
        CryptoNotFoundException ex = new CryptoNotFoundException("BTC", 42L);
        assertTrue(ex.getMessage().contains("BTC"));
        assertTrue(ex.getMessage().contains("42"));
    }

    // ── GlobalExceptionHandler ───────────────────────────────────────────────

    @Test
    void globalExceptionHandler_handleCryptoNotFoundException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        CryptoNotFoundException ex = new CryptoNotFoundException("BTC not found");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/crypto/BTC");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleCryptoNotFoundException(ex, webRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("BTC not found", response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void globalExceptionHandler_handleIllegalArgumentException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        IllegalArgumentException ex = new IllegalArgumentException("bad argument");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/crypto");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleIllegalArgumentException(ex, webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("bad argument", response.getBody().getMessage());
    }

    @Test
    void globalExceptionHandler_handleGlobalException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Exception ex = new RuntimeException("unexpected");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/anything");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleGlobalException(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("unexpected"));
    }

    // ── GlobalExceptionHandler.ErrorResponse (Lombok @Data) ─────────────────

    @Test
    void errorResponse_lombokGettersSetters() {
        LocalDateTime now = LocalDateTime.now();
        GlobalExceptionHandler.ErrorResponse er = new GlobalExceptionHandler.ErrorResponse(
                404, "not found", "/api/crypto", now);
        assertEquals(404, er.getStatus());
        assertEquals("not found", er.getMessage());
        assertEquals("/api/crypto", er.getPath());
        assertEquals(now, er.getTimestamp());

        GlobalExceptionHandler.ErrorResponse empty = new GlobalExceptionHandler.ErrorResponse();
        empty.setStatus(500);
        empty.setMessage("error");
        empty.setPath("/api");
        empty.setTimestamp(now);
        assertEquals(500, empty.getStatus());
        assertEquals("error", empty.getMessage());
    }
}

