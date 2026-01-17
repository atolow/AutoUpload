package com.example.auto.coupang.constants;

/**
 * 쿠팡 오픈 API 상수 클래스
 */
public final class CoupangApiConstants {
    
    private CoupangApiConstants() {
        // 인스턴스화 방지
    }
    
    /**
     * API 베이스 경로
     */
    public static final String API_BASE_PATH = "/v2/providers/seller_api/apis/api/v1";
    public static final String MARKETPLACE_API_BASE_PATH = API_BASE_PATH + "/marketplace";
    
    /**
     * 상품 관련 API 경로
     */
    public static final class Product {
        public static final String BASE = API_BASE_PATH + "/products";
        public static final String DETAIL = BASE + "/{productId}";
        public static final String STATUS = BASE + "/{productId}/status";
        public static final String CREATE = MARKETPLACE_API_BASE_PATH + "/seller-products";
        
        private Product() {}
    }
    
    /**
     * 주문 관련 API 경로
     */
    public static final class Order {
        public static final String BASE = API_BASE_PATH + "/orders";
        public static final String DETAIL = BASE + "/{orderId}";
        
        private Order() {}
    }
    
    /**
     * 상품 상태
     */
    public static final class ProductStatus {
        public static final String SALE = "SALE"; // 판매중
        public static final String OUT_OF_STOCK = "OUT_OF_STOCK"; // 품절
        public static final String SUSPENDED = "SUSPENDED"; // 판매중지
        
        private ProductStatus() {}
    }
    
    /**
     * 카테고리 관련 API 경로
     */
    public static final class Category {
        public static final String LIST = MARKETPLACE_API_BASE_PATH + "/meta/display-categories"; // 카테고리 목록 조회
        public static final String PREDICT = "/v2/providers/openapi/apis/api/v1/categorization/predict"; // 카테고리 추천
        public static final String METADATA = MARKETPLACE_API_BASE_PATH + "/meta/category-related-metas/display-category-codes/{displayCategoryCode}"; // 카테고리 메타데이터 조회
        
        private Category() {}
    }
    
    /**
     * 출고지 관련 API 경로
     */
    public static final class ShippingCenter {
        // 출고지 목록 조회 (최신 API 경로)
        // GET /v2/providers/marketplace_openapi/apis/api/v2/vendor/shipping-place/outbound
        public static final String LIST = "/v2/providers/marketplace_openapi/apis/api/v2/vendor/shipping-place/outbound";
        
        private ShippingCenter() {}
    }

    /**
     * 기본 페이지 크기
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 최대 페이지 크기
     */
    public static final int MAX_PAGE_SIZE = 100;
}
