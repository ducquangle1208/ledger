package com.LDQuang.mini_ledger.api.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(code.getHttpStatus())
                .error(httpReasonPhrase(code.getHttpStatus()))
                .code(code.name())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .details(ex.getDetails())
                .build();
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(400)
                .error("Bad Request")
                .code(ErrorCode.VALIDATION_ERROR.name())
                .message("Validation failed")
                .path(request.getRequestURI())
                .details(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(500)
                .error("Internal Server Error")
                .code(ErrorCode.INTERNAL_ERROR.name())
                .message("An unexpected error occurred")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.internalServerError().body(body);
    }

    private String httpReasonPhrase(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            default -> "Error";
        };
    }
}
