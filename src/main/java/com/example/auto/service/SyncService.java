package com.example.auto.service;

import com.example.auto.domain.Product;
import com.example.auto.domain.Store;
import com.example.auto.naver.client.NaverCommerceClient;
import com.example.auto.repository.OrderRepository;
import com.example.auto.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 네이버 스마트스토어 동기화 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SyncService {
    
    private final NaverCommerceClient naverCommerceClient;
    private final StoreService storeService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    
    /**
     * 상품 목록 동기화 (현재 스토어 자동 조회)
     * 
     * @return 동기화 결과 (성공 여부, 메시지, 동기화된 상품 수)
     * @throws IllegalArgumentException 스토어가 없을 때
     * @throws RuntimeException API 호출 실패 시 예외 발생
     */
    public Map<String, Object> syncProducts() {
        Store store = storeService.getCurrentStore()
                .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
        
        int syncedCount = syncProductsInternal(store);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "상품 동기화가 완료되었습니다.");
        response.put("syncedCount", syncedCount);
        return response;
    }
    
    /**
     * 상품 목록 동기화 (내부 메서드)
     * 
     * @param store 스토어 엔티티
     * @return 동기화된 상품 수
     * @throws RuntimeException API 호출 실패 시 예외 발생
     */
    public int syncProductsInternal(Store store) {
        log.info("상품 동기화 시작: {}", store.getStoreName());
        
        try {
            // searchProducts API를 사용하여 모든 상품 조회
            // 빈 검색 조건으로 첫 페이지부터 조회
            Map<String, Object> searchRequest = new java.util.HashMap<>();
            searchRequest.put("page", 1);
            searchRequest.put("size", 100); // 한 번에 최대 100개 조회
            
            // 동기 방식으로 처리하여 트랜잭션이 제대로 작동하도록 함
            Map<String, Object> response = naverCommerceClient.searchProducts(
                    store.getAccessToken(), 
                    store.getVendorId(),
                    searchRequest
            ).block(); // 비동기를 동기로 변환
            
            if (response == null) {
                log.warn("상품 목록 조회 응답이 null입니다: {}", store.getStoreName());
                throw new RuntimeException("상품 목록 조회 실패: 응답이 null입니다");
            }
            
            int syncedCount = parseAndSaveProducts(response, store);
            log.info("상품 동기화 완료: {} (동기화된 상품 수: {})", store.getStoreName(), syncedCount);
            return syncedCount;
            
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("상품 동기화 API 호출 실패: status={}, message={}", e.getStatusCode(), e.getMessage());
            log.error("응답 본문: {}", e.getResponseBodyAsString());
            throw new RuntimeException("상품 동기화 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("상품 동기화 중 오류 발생: {}", store.getStoreName(), e);
            throw new RuntimeException("상품 동기화 중 오류 발생: " + e.getMessage(), e);
        }
    }
    
    /**
     * API 응답을 파싱하여 Product 엔티티로 변환 후 저장
     * 
     * @param response 네이버 API 응답 (Map<String, Object>)
     * @param store 스토어 엔티티
     * @return 동기화된 상품 수
     */
    @Transactional
    public int parseAndSaveProducts(Map<String, Object> response, Store store) {
        if (response == null) {
            log.warn("API 응답이 null입니다.");
            return 0;
        }
        
        log.debug("API 응답 구조: {}", response.keySet());
        
        // 네이버 API 응답 구조에 따라 상품 목록 추출
        // 일반적인 구조: { "contents": [...], "totalCount": ... }
        List<Map<String, Object>> productList = new ArrayList<>();
        
        if (response.containsKey("contents")) {
            Object contents = response.get("contents");
            if (contents instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contentsList = (List<Map<String, Object>>) contents;
                productList = contentsList;
            }
        } else if (response.containsKey("data")) {
            // data 필드 안에 contents가 있을 수 있음
            Object data = response.get("data");
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) data;
                if (dataMap.containsKey("contents") && dataMap.get("contents") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> contentsList = (List<Map<String, Object>>) dataMap.get("contents");
                    productList = contentsList;
                }
            }
        } else {
            // 응답 자체가 배열일 수도 있음
            if (response instanceof Map && response.size() == 1) {
                // 단일 키의 값이 배열일 수 있음
                for (Object value : response.values()) {
                    if (value instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> contentsList = (List<Map<String, Object>>) value;
                        productList = contentsList;
                        break;
                    }
                }
            }
        }
        
        if (productList.isEmpty()) {
            log.warn("상품 목록을 찾을 수 없습니다. 응답 구조: {}", response.keySet());
            return 0;
        }
        
        log.info("파싱된 상품 항목 수: {}", productList.size());
        
        int syncedCount = 0;
        int updatedCount = 0;
        int createdCount = 0;
        LocalDateTime syncTime = LocalDateTime.now();
        
        // 네이버 API 응답 구조: 각 항목은 groupProductNo/originProductNo와 channelProducts 배열을 가짐
        // channelProducts 배열의 각 요소가 실제 채널 상품이므로 이를 개별 상품으로 처리
        for (Map<String, Object> productItem : productList) {
            try {
                // channelProducts 배열 추출
                Object channelProductsObj = productItem.get("channelProducts");
                if (!(channelProductsObj instanceof List)) {
                    log.debug("channelProducts가 배열이 아닙니다: {}", productItem.keySet());
                    continue;
                }
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> channelProducts = (List<Map<String, Object>>) channelProductsObj;
                
                if (channelProducts.isEmpty()) {
                    log.debug("channelProducts 배열이 비어있습니다.");
                    continue;
                }
                
                // 각 channelProduct를 개별 상품으로 처리
                for (Map<String, Object> channelProductData : channelProducts) {
                    try {
                        // originProductNo와 groupProductNo를 channelProductData에 추가 (참조용)
                        if (productItem.containsKey("originProductNo")) {
                            channelProductData.put("originProductNo", productItem.get("originProductNo"));
                        }
                        if (productItem.containsKey("groupProductNo")) {
                            channelProductData.put("groupProductNo", productItem.get("groupProductNo"));
                        }
                        
                        Product product = convertToProduct(channelProductData, store);
                        if (product == null) {
                            log.warn("상품 변환 실패: channelProductNo={}", channelProductData.get("channelProductNo"));
                            continue;
                        }
                        
                        // 기존 상품 조회 (productId 기준)
                        Product existingProduct = productRepository
                                .findByStoreAndProductId(store, product.getProductId())
                                .orElse(null);
                        
                        if (existingProduct != null) {
                            // 기존 상품 업데이트
                            updateProduct(existingProduct, product);
                            existingProduct.setLastSyncedAt(syncTime);
                            productRepository.save(existingProduct);
                            updatedCount++;
                            log.debug("상품 업데이트: {} ({})", existingProduct.getProductName(), existingProduct.getProductId());
                        } else {
                            // 새 상품 저장
                            product.setLastSyncedAt(syncTime);
                            productRepository.save(product);
                            createdCount++;
                            log.debug("상품 생성: {} ({})", product.getProductName(), product.getProductId());
                        }
                        
                        syncedCount++;
                    } catch (Exception e) {
                        log.error("채널 상품 저장 중 오류 발생: channelProductNo={}", 
                                channelProductData.get("channelProductNo"), e);
                    }
                }
            } catch (Exception e) {
                log.error("상품 항목 처리 중 오류 발생: {}", productItem, e);
            }
        }
        
        log.info("상품 동기화 결과 - 전체: {}, 신규: {}, 업데이트: {}", syncedCount, createdCount, updatedCount);
        return syncedCount;
    }
    
    /**
     * 네이버 API 응답 데이터를 Product 엔티티로 변환
     * 
     * @param productData 네이버 API 상품 데이터
     * @param store 스토어 엔티티
     * @return Product 엔티티 (변환 실패 시 null)
     */
    private Product convertToProduct(Map<String, Object> productData, Store store) {
        try {
            // 필수 필드 확인
            // 네이버 API에서는 channelProductNo가 실제 상품 ID
            String productId = extractString(productData, "channelProductNo", "productId", "id", "channelProductId");
            String productName = extractString(productData, "name", "productName", "title");
            
            if (productId == null || productId.isEmpty()) {
                log.warn("productId를 찾을 수 없습니다: {}", productData.keySet());
                return null;
            }
            
            if (productName == null || productName.isEmpty()) {
                log.warn("productName을 찾을 수 없습니다. productId: {}", productId);
                productName = "상품명 없음"; // 기본값 설정
            }
            
            // 가격 정보 추출
            BigDecimal price = extractBigDecimal(productData, "salePrice", "price", "regularPrice", "originPrice");
            if (price == null) {
                price = BigDecimal.ZERO; // 기본값
            }
            
            // 할인가 추출 (discountedPrice 또는 salePrice 사용)
            BigDecimal salePrice = extractBigDecimal(productData, "discountedPrice", "salePrice", "discountPrice");
            if (salePrice == null) {
                salePrice = price; // 할인가가 없으면 정가와 동일
            }
            
            // 재고 수량 추출
            Integer stock = extractInteger(productData, "stockQuantity", "stock", "quantity", "inventory");
            
            // 이미지 URL 추출 (representativeImage.url 또는 직접 imageUrl)
            String imageUrl = null;
            Object representativeImage = productData.get("representativeImage");
            if (representativeImage instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> imageMap = (Map<String, Object>) representativeImage;
                imageUrl = extractString(imageMap, "url", "imageUrl");
            }
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = extractString(productData, "imageUrl", "image", "thumbnailUrl", "thumbnail");
            }
            
            // 상품 URL 추출
            String productUrl = extractString(productData, "productUrl", "url", "link");
            
            // 설명 추출
            String description = extractString(productData, "description", "detail", "content");
            
            // 상태 추출 및 변환
            Product.ProductStatus status = extractProductStatus(productData);
            
            // Product 엔티티 생성
            return Product.builder()
                    .store(store)
                    .productId(productId)
                    .productName(productName)
                    .description(description)
                    .price(price)
                    .salePrice(salePrice)
                    .stock(stock)
                    .imageUrl(imageUrl)
                    .productUrl(productUrl)
                    .status(status)
                    .build();
                    
        } catch (Exception e) {
            log.error("상품 변환 중 오류 발생: {}", productData, e);
            return null;
        }
    }
    
    /**
     * 기존 상품 정보 업데이트
     */
    private void updateProduct(Product existing, Product newData) {
        existing.setProductName(newData.getProductName());
        existing.setDescription(newData.getDescription());
        existing.setPrice(newData.getPrice());
        existing.setSalePrice(newData.getSalePrice());
        existing.setStock(newData.getStock());
        existing.setImageUrl(newData.getImageUrl());
        existing.setProductUrl(newData.getProductUrl());
        existing.setStatus(newData.getStatus());
    }
    
    /**
     * Map에서 문자열 값 추출 (여러 키 시도)
     */
    private String extractString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }
    
    /**
     * Map에서 BigDecimal 값 추출
     */
    private BigDecimal extractBigDecimal(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                try {
                    if (value instanceof Number) {
                        return BigDecimal.valueOf(((Number) value).doubleValue());
                    } else if (value instanceof String) {
                        return new BigDecimal((String) value);
                    }
                } catch (Exception e) {
                    log.debug("BigDecimal 변환 실패: {} = {}", key, value);
                }
            }
        }
        return null;
    }
    
    /**
     * Map에서 Integer 값 추출
     */
    private Integer extractInteger(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                try {
                    if (value instanceof Number) {
                        return ((Number) value).intValue();
                    } else if (value instanceof String) {
                        return Integer.parseInt((String) value);
                    }
                } catch (Exception e) {
                    log.debug("Integer 변환 실패: {} = {}", key, value);
                }
            }
        }
        return null;
    }
    
    /**
     * 네이버 API 상태 값을 ProductStatus로 변환
     */
    private Product.ProductStatus extractProductStatus(Map<String, Object> productData) {
        String statusType = extractString(productData, "statusType", "status", "productStatus", "saleStatus");
        
        if (statusType == null) {
            return Product.ProductStatus.ON_SALE; // 기본값
        }
        
        statusType = statusType.toUpperCase();
        
        // 네이버 API 상태 값 매핑
        switch (statusType) {
            case "SALE":
            case "ON_SALE":
            case "ONSALE":
                return Product.ProductStatus.ON_SALE;
            case "OUTOFSTOCK":
            case "OUT_OF_STOCK":
            case "SOLD_OUT":
                return Product.ProductStatus.OUT_OF_STOCK;
            case "SALE_STOP":
            case "SALE_STOPPED":
            case "STOPPED":
                return Product.ProductStatus.SALE_STOPPED;
            default:
                log.debug("알 수 없는 상태 값: {}, 기본값(ON_SALE) 사용", statusType);
                return Product.ProductStatus.ON_SALE;
        }
    }
    
    /**
     * 주문 목록 동기화 (내부 메서드)
     */
    public void syncOrdersInternal(Store store, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("주문 동기화 시작: {} ({} ~ {})", store.getStoreName(), startDate, endDate);
        
        try {
            naverCommerceClient.getOrders(store.getAccessToken(), store.getVendorId(), startDate, endDate)
                    .subscribe(response -> {
                        // TODO: API 응답을 파싱하여 Order 엔티티로 변환 후 저장
                        log.info("주문 동기화 완료: {}", store.getStoreName());
                    }, error -> {
                        log.error("주문 동기화 실패: {}", store.getStoreName(), error);
                    });
        } catch (Exception e) {
            log.error("주문 동기화 중 오류 발생: {}", store.getStoreName(), e);
        }
    }
    
    /**
     * 재고 동기화
     */
    public void syncInventory(Store store, Product product) {
        log.info("재고 동기화 시작: {} - {}", store.getStoreName(), product.getProductName());
        
        try {
            naverCommerceClient.getInventory(store.getAccessToken(), store.getVendorId(), product.getProductId())
                    .subscribe(response -> {
                        // TODO: API 응답을 파싱하여 재고 수량 업데이트
                        log.info("재고 동기화 완료: {} - {}", store.getStoreName(), product.getProductName());
                    }, error -> {
                        log.error("재고 동기화 실패: {} - {}", store.getStoreName(), product.getProductName(), error);
                    });
        } catch (Exception e) {
            log.error("재고 동기화 중 오류 발생: {} - {}", store.getStoreName(), product.getProductName(), e);
        }
    }
    
    /**
     * 모든 활성 스토어의 상품 동기화
     */
    public void syncAllProducts() {
        List<Store> activeStores = storeService.getActiveStores();
        for (Store store : activeStores) {
            syncProductsInternal(store);
        }
    }
    
    /**
     * 주문 목록 동기화 (현재 스토어 자동 조회)
     */
    public Map<String, Object> syncOrders(LocalDateTime startDate, LocalDateTime endDate) {
        Store store = storeService.getCurrentStore()
                .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
        
        LocalDateTime finalStartDate = startDate != null ? startDate : LocalDateTime.now().minusDays(7);
        LocalDateTime finalEndDate = endDate != null ? endDate : LocalDateTime.now();
        
        syncOrdersInternal(store, finalStartDate, finalEndDate);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "주문 동기화가 시작되었습니다.");
        return response;
    }
    
    /**
     * 모든 활성 스토어의 주문 동기화 (최근 7일)
     */
    public void syncAllOrders() {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(7);
        
        List<Store> activeStores = storeService.getActiveStores();
        for (Store store : activeStores) {
            syncOrdersInternal(store, startDate, endDate);
        }
    }
}

