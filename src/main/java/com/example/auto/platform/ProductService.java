package com.example.auto.platform;

import com.example.auto.dto.ProductRequest;
import com.example.auto.dto.ProductSearchRequest;

import java.util.Map;
import java.util.Optional;

/**
 * 플랫폼별 상품 관리 서비스 인터페이스
 * 각 플랫폼(네이버, 쿠팡, 11번가)은 이 인터페이스를 구현합니다.
 */
public interface ProductService {
    
    /**
     * 상품 생성
     * 
     * @param storeId 스토어 ID
     * @param request 상품 생성 요청
     * @return 생성된 상품 정보
     */
    Map<String, Object> createProduct(Long storeId, ProductRequest request);
    
    /**
     * 상품 검색
     * 
     * @param storeId 스토어 ID
     * @param request 검색 요청
     * @return 검색 결과
     */
    Map<String, Object> searchProducts(Long storeId, ProductSearchRequest request);
    
    /**
     * 현재 등록된 스토어 조회
     * 
     * @return 스토어 정보 (Optional)
     */
    Optional<com.example.auto.domain.Store> getCurrentStore();
}

