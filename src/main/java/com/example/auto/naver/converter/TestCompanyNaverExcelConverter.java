package com.example.auto.naver.converter;

import com.example.auto.dto.ProductRequest;
import com.example.auto.naver.constants.NaverApiConstants;
import com.example.auto.naver.service.NaverCategoryMappingService;
import com.example.auto.naver.util.NaverStatusConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * CompanyTest 회사의 네이버 엑셀 데이터를 ProductRequest로 변환하는 컨버터
 * 
 * 엑셀 컬럼 구조:
 * - 상품ID(내부용)
 * - 상품명
 * - 카테고리
 * - 판매가
 * - 재고수량
 * - 옵션명1, 옵션값1
 * - 옵션명2, 옵션값2
 * - 브랜드
 * - 모델명
 * - 바코드
 * - 상세설명
 * - 대표이미지URL
 * - 추가이미지URL1
 * - 배송비
 * - 배송방법
 * - 원산지
 * - 제조사
 * - 과세구분
 * - 판매상태
 * - 전시상태
 * - guideId
 */
@Slf4j
@Component("testCompanyNaverExcelConverter")
@org.springframework.context.annotation.Primary
@RequiredArgsConstructor
public class TestCompanyNaverExcelConverter implements NaverExcelConverter {
    
    private final NaverCategoryMappingService categoryMappingService;
    
    /**
     * 엑셀 행 데이터를 ProductRequest로 변환
     * 
     * @param rowData 엑셀 행 데이터 (Map)
     * @return ProductRequest 객체
     * @throws IllegalArgumentException 필수 필드가 없거나 형식이 잘못된 경우
     */
    @Override
    public ProductRequest convert(Map<String, Object> rowData) {
        if (rowData == null || rowData.isEmpty()) {
            throw new IllegalArgumentException("행 데이터가 비어있습니다.");
        }
        
        Integer rowNumber = (Integer) rowData.get("_rowNumber");
        if (rowNumber == null) {
            rowNumber = 0;
        }
        
        log.debug("엑셀 행 {} 변환 시작 (CompanyTest)", rowNumber);
        
        ProductRequest.ProductRequestBuilder builder = ProductRequest.builder();
        
        // 필수 필드: 상품명
        String name = getStringValue(rowData, "상품명");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 상품명은 필수입니다.", rowNumber));
        }
        builder.name(name.trim());
        
        // 필수 필드: 카테고리 (카테고리대, 카테고리중, 카테고리소 또는 단일 카테고리 컬럼)
        String categoryPath = null;
        
        // 카테고리대, 카테고리중, 카테고리소가 있는 경우 조합
        String categoryLarge = getStringValue(rowData, "카테고리대");
        String categoryMedium = getStringValue(rowData, "카테고리중");
        String categorySmall = getStringValue(rowData, "카테고리소");
        
        if (categoryLarge != null && !categoryLarge.trim().isEmpty()) {
            // 카테고리대, 카테고리중, 카테고리소로 경로 구성
            StringBuilder pathBuilder = new StringBuilder();
            pathBuilder.append(categoryLarge.trim());
            
            if (categoryMedium != null && !categoryMedium.trim().isEmpty()) {
                pathBuilder.append(" > ").append(categoryMedium.trim());
            }
            
            if (categorySmall != null && !categorySmall.trim().isEmpty()) {
                pathBuilder.append(" > ").append(categorySmall.trim());
            }
            
            categoryPath = pathBuilder.toString();
        } else {
            // 기존 방식: 단일 카테고리 컬럼 사용
            categoryPath = getStringValue(rowData, "카테고리");
        }
        
        if (categoryPath == null || categoryPath.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 카테고리는 필수입니다. (카테고리대 또는 카테고리 컬럼 필요)", rowNumber));
        }
        
        String trimmedCategoryPath = categoryPath.trim();
        
        // 카테고리 경로 저장 (카테고리별 원산지 정보 처리용)
        builder.categoryPath(trimmedCategoryPath);
        
        // 숫자 ID인지 확인
        if (categoryMappingService.isNumericCategoryId(trimmedCategoryPath)) {
            // 이미 숫자 ID이면 그대로 사용
            builder.leafCategoryId(trimmedCategoryPath);
        } else {
            // 한글 경로인 경우 매핑 서비스를 통해 변환 시도
            String mappedCategoryId = categoryMappingService.convertToCategoryId(trimmedCategoryPath);
            if (mappedCategoryId != null) {
                log.info("행 {}: 카테고리 경로를 ID로 변환: {} -> {}", rowNumber, trimmedCategoryPath, mappedCategoryId);
                builder.leafCategoryId(mappedCategoryId);
            } else {
                // 매핑이 없으면 에러 발생 (잘못된 카테고리 ID로 등록 시도 방지)
                throw new IllegalArgumentException(String.format(
                        "행 %d: 카테고리 매핑을 찾을 수 없습니다: '%s'. " +
                        "엑셀 파일의 카테고리 컬럼을 숫자 ID로 변경하거나, " +
                        "네이버 스마트스토어에서 확인한 정확한 카테고리 경로를 사용하세요.", 
                        rowNumber, trimmedCategoryPath));
            }
        }
        
        // 필수 필드: 판매가
        BigDecimal salePrice = getBigDecimalValue(rowData, "판매가");
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(String.format("행 %d: 판매가는 필수이며 0보다 커야 합니다.", rowNumber));
        }
        builder.salePrice(salePrice);
        
        // 필수 필드: 재고수량
        Integer stockQuantity = getIntegerValue(rowData, "재고수량");
        if (stockQuantity == null || stockQuantity < 0) {
            throw new IllegalArgumentException(String.format("행 %d: 재고수량은 필수이며 0 이상이어야 합니다.", rowNumber));
        }
        builder.stockQuantity(stockQuantity);
        
        // 필수 필드: 상세설명 (상품상세설명 컬럼 사용)
        String detailContent = getStringValue(rowData, "상품상세설명");
        if (detailContent == null || detailContent.trim().isEmpty()) {
            // 폴백: "상세설명" 컬럼도 시도
            detailContent = getStringValue(rowData, "상세설명");
        }
        if (detailContent == null || detailContent.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 상품상세설명은 필수입니다.", rowNumber));
        }
        builder.detailContent(detailContent.trim());
        
        // 필수 필드: 대표이미지URL
        String mainImageUrl = getStringValue(rowData, "대표이미지URL");
        if (mainImageUrl == null || mainImageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 대표이미지URL은 필수입니다.", rowNumber));
        }
        
        // 이미지 리스트 생성
        List<String> images = new ArrayList<>();
        images.add(mainImageUrl.trim());
        
        // 추가이미지URL1
        String additionalImageUrl1 = getStringValue(rowData, "추가이미지URL1");
        if (additionalImageUrl1 != null && !additionalImageUrl1.trim().isEmpty()) {
            images.add(additionalImageUrl1.trim());
        }
        
        builder.images(images);
        
        // 선택 필드: 판매상태 (한글 -> 영문 Enum 변환)
        String statusType = getStringValue(rowData, "판매상태");
        if (statusType != null && !statusType.trim().isEmpty()) {
            builder.statusType(NaverStatusConverter.convertStatusType(statusType.trim()));
        } else {
            builder.statusType(NaverApiConstants.DEFAULT_STATUS_TYPE); // 기본값: 판매중
        }
        
        // 선택 필드: 전시상태 (한글 -> 영문 Enum 변환)
        String channelProductDisplayStatusType = getStringValue(rowData, "전시상태");
        if (channelProductDisplayStatusType != null && !channelProductDisplayStatusType.trim().isEmpty()) {
            builder.channelProductDisplayStatusType(NaverStatusConverter.convertDisplayStatusType(channelProductDisplayStatusType.trim()));
        } else {
            builder.channelProductDisplayStatusType(NaverApiConstants.DEFAULT_DISPLAY_STATUS_TYPE); // 기본값: 전시
        }
        
        // 선택 필드: 배송 정보
        ProductRequest.DeliveryInfo deliveryInfo = createDeliveryInfo(rowData, rowNumber);
        if (deliveryInfo != null) {
            builder.deliveryInfo(deliveryInfo);
        }
        
        // 선택 필드: 브랜드
        String brandName = getStringValue(rowData, "브랜드");
        if (brandName != null && !brandName.trim().isEmpty()) {
            builder.brandName(brandName.trim());
        }
        
        // 선택 필드: 모델명
        String modelName = getStringValue(rowData, "모델명");
        if (modelName != null && !modelName.trim().isEmpty()) {
            builder.modelName(modelName.trim());
        }
        
        // 선택 필드: 제조사
        String manufacturer = getStringValue(rowData, "제조사");
        if (manufacturer != null && !manufacturer.trim().isEmpty()) {
            builder.manufacturer(manufacturer.trim());
        }
        
        // 선택 필드: 원산지
        String originArea = getStringValue(rowData, "원산지");
        if (originArea != null && !originArea.trim().isEmpty()) {
            builder.originArea(originArea.trim());
        }
        
        // 선택 필드: 과세구분
        String taxType = getStringValue(rowData, "과세구분");
        if (taxType != null && !taxType.trim().isEmpty()) {
            builder.taxType(taxType.trim().toUpperCase());
        }
        
        ProductRequest request = builder.build();
        log.debug("엑셀 행 {} 변환 완료 (CompanyTest): 상품명={}", rowNumber, name);
        
        return request;
    }
    
    /**
     * 배송 정보 생성
     */
    private ProductRequest.DeliveryInfo createDeliveryInfo(Map<String, Object> rowData, Integer rowNumber) {
        String deliveryType = getStringValue(rowData, "배송방법");
        String deliveryFeeStr = getStringValue(rowData, "배송비");
        
        // 배송 정보가 없으면 기본값 사용 (ProductService에서 처리)
        if (deliveryType == null && deliveryFeeStr == null) {
            return null;
        }
        
        ProductRequest.DeliveryInfo.DeliveryInfoBuilder builder = ProductRequest.DeliveryInfo.builder();
        
        // 배송방법 (한글 -> 영문 Enum 변환)
        // 엑셀의 "배송방법" 컬럼에 택배사 이름(한진택배, CJ대한통운 등)이 들어올 수 있음
        String finalDeliveryType;
        if (deliveryType != null && !deliveryType.trim().isEmpty()) {
            finalDeliveryType = NaverStatusConverter.convertDeliveryType(deliveryType.trim());
            builder.deliveryType(finalDeliveryType);
        } else {
            finalDeliveryType = NaverApiConstants.DEFAULT_DELIVERY_TYPE; // 기본값: 택배배송
            builder.deliveryType(finalDeliveryType);
        }
        
        // 택배사 코드 추론 (DELIVERY일 때만)
        if (NaverApiConstants.DeliveryType.DELIVERY.equals(finalDeliveryType)) {
            String deliveryCompany = extractDeliveryCompany(deliveryType);
            if (deliveryCompany != null) {
                builder.deliveryCompany(deliveryCompany);
            } else {
                builder.deliveryCompany(NaverApiConstants.DeliveryCompany.CJGLS); // 기본값: CJ대한통운
            }
        }
        
        // 배송비
        if (deliveryFeeStr != null && !deliveryFeeStr.trim().isEmpty()) {
            try {
                Integer deliveryFee = Integer.parseInt(deliveryFeeStr.trim());
                builder.deliveryFee(deliveryFee);
            } catch (NumberFormatException e) {
                log.warn("행 {}: 배송비 형식이 올바르지 않습니다: {}", rowNumber, deliveryFeeStr);
            }
        }
        
        return builder.build();
    }
    
    /**
     * 배송방법 문자열에서 택배사 코드 추출
     * 네이버 API 택배사 코드: CJGLS(CJ대한통운), HANJIN(한진택배), KGB(로젠택배), HYUNDAI(롯데택배), EPOST(우체국택배)
     */
    private String extractDeliveryCompany(String deliveryType) {
        if (deliveryType == null || deliveryType.trim().isEmpty()) {
            return null;
        }
        
        String normalized = deliveryType.trim();
        
        // 택배사 이름 -> 택배사 코드 변환
        if (normalized.contains("한진") || normalized.contains("HANJIN")) {
            return NaverApiConstants.DeliveryCompany.HANJIN;
        } else if (normalized.contains("CJ") || normalized.contains("대한통운") || normalized.contains("CJGLS")) {
            return NaverApiConstants.DeliveryCompany.CJGLS;
        } else if (normalized.contains("로젠") || normalized.contains("KGB")) {
            return NaverApiConstants.DeliveryCompany.KGB;
        } else if (normalized.contains("롯데") || normalized.contains("HYUNDAI")) {
            return NaverApiConstants.DeliveryCompany.HYUNDAI;
        } else if (normalized.contains("우체국") || normalized.contains("EPOST")) {
            return NaverApiConstants.DeliveryCompany.EPOST;
        }
        
        // 이미 코드 형식이면 그대로 사용
        String upper = normalized.toUpperCase();
        if (upper.equals(NaverApiConstants.DeliveryCompany.CJGLS) || 
            upper.equals(NaverApiConstants.DeliveryCompany.HANJIN) || 
            upper.equals(NaverApiConstants.DeliveryCompany.KGB) || 
            upper.equals(NaverApiConstants.DeliveryCompany.HYUNDAI) || 
            upper.equals(NaverApiConstants.DeliveryCompany.EPOST)) {
            return upper;
        }
        
        return null; // 알 수 없는 값
    }
    
    /**
     * Map에서 String 값 추출
     */
    private String getStringValue(Map<String, Object> rowData, String key) {
        Object value = rowData.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }
    
    /**
     * Map에서 Integer 값 추출
     */
    private Integer getIntegerValue(Map<String, Object> rowData, String key) {
        Object value = rowData.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                String str = ((String) value).trim();
                if (str.isEmpty()) {
                    return null;
                }
                // 소수점 제거
                if (str.contains(".")) {
                    return (int) Double.parseDouble(str);
                }
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                log.warn("행 {}: {} 값을 Integer로 변환 실패: {}", rowData.get("_rowNumber"), key, value);
                return null;
            }
        }
        return null;
    }
    
    /**
     * Map에서 BigDecimal 값 추출
     */
    private BigDecimal getBigDecimalValue(Map<String, Object> rowData, String key) {
        Object value = rowData.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            try {
                String str = ((String) value).trim();
                if (str.isEmpty()) {
                    return null;
                }
                // 쉼표 제거 (예: "10,000" -> "10000")
                str = str.replace(",", "");
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                log.warn("행 {}: {} 값을 BigDecimal로 변환 실패: {}", rowData.get("_rowNumber"), key, value);
                return null;
            }
        }
        return null;
    }
}
