package com.example.auto.controller;

import com.example.auto.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 동기화 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {
    
    private final SyncService syncService;
    
    /**
     * 상품 동기화 (독립 실행형: 현재 스토어만 동기화)
     */
    @PostMapping("/products")
    public ResponseEntity<Map<String, Object>> syncProducts() {
        try {
            Map<String, Object> result = syncService.syncProducts();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("상품 동기화 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            log.error("상품 동기화 실패", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "상품 동기화 실패: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 주문 동기화 (독립 실행형: 현재 스토어만 동기화)
     */
    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> syncOrders(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        
        try {
            Map<String, Object> result = syncService.syncOrders(startDate, endDate);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("주문 동기화 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            log.error("주문 동기화 실패", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "주문 동기화 실패: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

