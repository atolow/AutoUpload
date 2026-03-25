package com.example.auto.coupang.service;

import com.example.auto.coupang.client.CoupangApiClient;
import com.example.auto.coupang.config.CoupangProperties;
import com.example.auto.domain.Store;
import com.example.auto.dto.ProductRequest;
import com.example.auto.dto.ProductSearchRequest;
import com.example.auto.platform.ProductService;
import com.example.auto.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 쿠팡 상품 관리 서비스
 */
@Slf4j
@Service("coupangProductService")
@RequiredArgsConstructor
@Transactional
public class CoupangProductService implements ProductService {
    
    private final CoupangApiClient coupangApiClient;
    private final CoupangProperties coupangProperties;
    private final StoreService storeService;
    
    /**
     * 현재 등록된 스토어 조회 (독립 실행형용)
     */
    @Override
    public Optional<Store> getCurrentStore() {
        return storeService.getCurrentStore();
    }
    
    /**
     * 상품 생성
     * 
     * @param storeId 스토어 ID
     * @param request 상품 생성 요청
     * @return 생성된 상품 정보
     */
    @Override
    public Map<String, Object> createProduct(Long storeId, ProductRequest request) {
        log.info("쿠팡 상품 등록 시작: storeId={}, 상품명={}", storeId, request.getName());
        
        // Store 조회 (vendorId 확인용)
        Store store = storeService.getCurrentStore()
                .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다."));
        
        // ProductRequest를 쿠팡 API 형식으로 변환
        // 엑셀에서 직접 변환하는 경우와 API 요청에서 변환하는 경우를 구분해야 함
        // 여기서는 간단한 변환만 수행하고, 실제로는 엑셀 변환기를 사용하는 것이 좋음
        
        // 쿠팡 API 형식으로 변환
        Map<String, Object> coupangProductData = convertToCoupangFormat(request, store.getVendorId());
        
        // 쿠팡 API 호출
        Map<String, Object> result = coupangApiClient.createProduct(coupangProductData).block();
        
        log.info("쿠팡 상품 등록 완료: storeId={}, 상품명={}", storeId, request.getName());
        
        return result;
    }
    
    /**
     * ProductRequest를 쿠팡 API 형식으로 변환
     */
    private Map<String, Object> convertToCoupangFormat(ProductRequest request, String vendorId) {
        Map<String, Object> productData = new java.util.HashMap<>();
        
        productData.put("vendorId", vendorId);
        productData.put("displayCategoryCode", request.getLeafCategoryId());
        productData.put("sellerProductName", request.getName());
        productData.put("displayProductName", request.getName());
        
        // 판매 기간 설정
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime endDate = now.plusYears(1);
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        productData.put("saleStartedAt", now.format(formatter));
        productData.put("saleEndedAt", endDate.format(formatter));
        
        // items 생성
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        Map<String, Object> item = new java.util.HashMap<>();
        item.put("itemName", request.getName());
        item.put("originalPrice", request.getSalePrice().intValue());
        item.put("salePrice", request.getSalePrice().intValue());
        item.put("stockQuantity", request.getStockQuantity());
        items.add(item);
        productData.put("items", items);
        
        // 반품 정보 (기본값)
        productData.put("returnChargeName", "반품담당자");
        productData.put("returnZipCode", "00000");
        productData.put("returnAddress", "반품 주소를 입력하세요");
        
        // 선택 필드
        if (request.getBrandName() != null) {
            productData.put("brand", request.getBrandName());
        }
        
        // 승인 요청
        productData.put("requested", true);
        
        return productData;
    }
    
    /**
     * 상품 검색
     * 
     * @param storeId 스토어 ID
     * @param request 검색 요청
     * @return 검색 결과
     */
    @Override
    public Map<String, Object> searchProducts(Long storeId, ProductSearchRequest request) {
        log.info("쿠팡 상품 검색: storeId={}, page={}, size={}", storeId, request.getPage(), request.getSize());
        
        Integer page = request.getPage() != null ? request.getPage() : 1;
        Integer size = request.getSize() != null ? request.getSize() : 10;
        
        Map<String, Object> result = coupangApiClient.getProducts(page, size).block();
        
        return result != null ? result : Map.of();
    }
}
