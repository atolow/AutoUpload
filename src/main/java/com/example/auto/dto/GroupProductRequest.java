package com.example.auto.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * 그룹상품 등록 요청 DTO
 * 네이버 커머스 API v2 standard-group-products 형식
 * 
 * 실제 API 구조:
 * - groupProduct (최상위 객체)
 *   - leafCategoryId, name, guideId (그룹 레벨)
 *   - specificProducts (개별 상품 배열)
 *   - smartstoreGroupChannel (채널 정보)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupProductRequest {
    
    /**
     * 그룹상품 정보 (네이버 API 형식)
     */
    private GroupProduct groupProduct;
    
    /**
     * 사용자 친화적 형식: 간단한 필드들
     */
    private String groupName;
    private String leafCategoryId;
    private Long guideId;
    private List<SpecificProduct> specificProducts;
    private SmartstoreGroupChannel smartstoreGroupChannel;
    private WindowGroupChannel windowGroupChannel;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GroupProduct {
        private String leafCategoryId;
        private String name;
        private Long guideId;
        private String brandName;
        private Long brandId;
        private String manufacturerName;
        private Boolean itselfProductionProductYn;
        private String taxType;
        private String saleType;
        private Boolean minorPurchasable;
        private Boolean brandCertificationYn;
        private Map<String, Object> productInfoProvidedNotice;
        private Map<String, Object> afterServiceInfo;
        private String sellerCommentContent;
        private Map<String, Object> supplementProductInfo;
        private Map<String, Object> seoInfo;
        private String commonDetailContent;
        private Map<String, Object> productSize;
        private List<SpecificProduct> specificProducts;
        private SmartstoreGroupChannel smartstoreGroupChannel;
        private WindowGroupChannel windowGroupChannel;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpecificProduct {
        // 판매 옵션 정보
        private List<Map<String, Object>> saleOptions;
        
        // 상품 정보
        private String detailContent;
        private String detailContentTempId;
        private Long salePrice;
        private Long normalPrice;
        private Integer stockQuantity;
        private Map<String, Object> images;
        private Map<String, Object> deliveryInfo;
        private Map<String, Object> detailAttribute;
        private Map<String, Object> customerBenefit;
        private List<Map<String, Object>> productLogistics;
        
        // 사용자 친화적 형식
        private String name; // 판매 옵션명 (자동 생성됨)
        private List<String> imageUrls; // 간단한 이미지 URL 리스트
    }
    
    /**
     * 스마트스토어 그룹상품 공통 채널 정보
     * API 문서: bbsSeq만 필요 (콘텐츠 게시글 일련번호, 공지사항)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SmartstoreGroupChannel {
        /**
         * 콘텐츠 게시글 일련번호 (integer<int64>)
         * 공지사항
         */
        private Long bbsSeq;
    }
    
    /**
     * 윈도 그룹상품 채널 정보
     * API 문서: channelNo (required), best (optional), bbsSeq (optional)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WindowGroupChannel {
        /**
         * 윈도 채널 상품 채널 번호 (integer<int64>)
         * required - 전시할 윈도 채널 선택
         */
        private Long channelNo;
        
        /**
         * 베스트 여부(윈도 채널 전용) (boolean)
         * 미입력 시 false로 저장됩니다.
         */
        private Boolean best;
        
        /**
         * 콘텐츠 게시글 일련번호 (integer<int64>)
         * 공지사항
         */
        private Long bbsSeq;
    }
}
