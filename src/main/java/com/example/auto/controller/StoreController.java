package com.example.auto.controller;

import com.example.auto.domain.Store;
import com.example.auto.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 스토어 관리 REST API
 */
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {
    
    private final StoreService storeService;
    
    /**
     * 스토어 등록
     */
    @PostMapping
    public ResponseEntity<Store> createStore(@RequestBody Store store) {
        Store createdStore = storeService.createStore(store);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdStore);
    }
    
    /**
     * 현재 등록된 스토어 조회 (독립 실행형: 스토어는 1개만 존재)
     */
    @GetMapping
    public ResponseEntity<Store> getCurrentStore() {
        return storeService.getCurrentStore()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 스토어 수정 (현재 스토어만 수정 가능)
     */
    @PutMapping
    public ResponseEntity<Store> updateStore(@RequestBody Store store) {
        return storeService.getCurrentStore()
                .map(currentStore -> {
                    try {
                        Store updatedStore = storeService.updateStore(currentStore.getId(), store);
                        return ResponseEntity.ok(updatedStore);
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.notFound().<Store>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 스토어 삭제 (현재 스토어 삭제)
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteStore() {
        return storeService.getCurrentStore()
                .map(store -> {
                    storeService.deleteStore(store.getId());
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

