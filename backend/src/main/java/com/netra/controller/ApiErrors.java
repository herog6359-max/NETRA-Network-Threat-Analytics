package com.netra.controller;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    @ExceptionHandler({IllegalStateException.class, org.springframework.dao.DataAccessException.class})
    public ResponseEntity<?> unavailable(RuntimeException e) { return ResponseEntity.status(503).body(Map.of("error", "Operation could not be saved or completed. Check services and retry.")); }
}
