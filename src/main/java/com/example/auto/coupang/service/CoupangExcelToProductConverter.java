package com.example.auto.coupang.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Set;
import java.util.HashSet;

/**
 * 쿠팡 엑셀 데이터를 쿠팡 API 형식으로 변환하는 컨버터
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoupangExcelToProductConverter {
    
    private final CoupangCategoryMappingService categoryMappingService;
    private final com.example.auto.coupang.client.CoupangApiClient coupangApiClient;
    private final com.example.auto.coupang.config.CoupangProperties coupangProperties;
    
    // 출고지 코드 캐시 (한 번 조회하면 재사용)
    private Long cachedOutboundShippingPlaceCode = null;
    
    /**
     * 엑셀 행 데이터를 쿠팡 API 상품 등록 형식으로 변환
     * 
     * @param rowData 엑셀 행 데이터 (Map)
     * @param vendorId 업체코드
     * @return 쿠팡 API 상품 등록 데이터 (Map)
     * @throws IllegalArgumentException 필수 필드가 없거나 형식이 잘못된 경우
     */
    public Map<String, Object> convert(Map<String, Object> rowData, String vendorId) {
        if (rowData == null || rowData.isEmpty()) {
            throw new IllegalArgumentException("행 데이터가 비어있습니다.");
        }
        
        Integer rowNumber = (Integer) rowData.get("_rowNumber");
        if (rowNumber == null) {
            rowNumber = 0;
        }
        
        log.debug("엑셀 행 {} 쿠팡 형식 변환 시작", rowNumber);
        
        Map<String, Object> productData = new HashMap<>();
        
        // 필수 필드: vendorId
        productData.put("vendorId", vendorId);
        
        // 필수 필드: displayCategoryCode (카테고리)
        // 쿠팡 API는 카테고리 코드를 Long 타입으로 요구함
        String categoryCodeStr = getStringValue(rowData, "카테고리");
        if (categoryCodeStr == null || categoryCodeStr.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 카테고리는 필수입니다.", rowNumber));
        }
        
        // 카테고리 코드 처리
        Long categoryCode;
        String trimmed = categoryCodeStr.trim();
        
        // 숫자인지 확인
        if (trimmed.matches("\\d+")) {
            // 숫자 코드인 경우 그대로 사용
            try {
                categoryCode = Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(String.format(
                    "행 %d: 카테고리 코드를 숫자로 변환할 수 없습니다. (값: '%s')", 
                    rowNumber, categoryCodeStr));
            }
        } else {
            // 한글 카테고리 경로인 경우 매핑 서비스를 통해 변환 시도
            log.info("행 {}: 한글 카테고리 경로 감지: '{}', 매핑 서비스를 통해 변환 시도...", rowNumber, trimmed);
            
            String mappedCode = categoryMappingService.convertToCategoryCode(trimmed);
            
            if (mappedCode != null) {
                try {
                    categoryCode = Long.parseLong(mappedCode);
                    log.info("행 {}: 카테고리 매핑 성공: '{}' -> {}", rowNumber, trimmed, categoryCode);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(String.format(
                        "행 %d: 매핑된 카테고리 코드를 숫자로 변환할 수 없습니다. (값: '%s')", 
                        rowNumber, mappedCode));
                }
            } else {
                // 매핑 실패 시 상품명을 기반으로 카테고리 추천 시도
                String productName = getStringValue(rowData, "상품명");
                String productDescription = getStringValue(rowData, "상세설명");
                String brand = getStringValue(rowData, "브랜드");
                
                log.info("행 {}: 카테고리 매핑 실패, 상품명 기반 추천 시도: '{}'", rowNumber, productName);
                
                String predictedCode = categoryMappingService.predictCategory(
                        productName != null ? productName : "",
                        productDescription,
                        brand
                );
                
                if (predictedCode != null) {
                    try {
                        categoryCode = Long.parseLong(predictedCode);
                        log.info("행 {}: 카테고리 추천 성공: '{}' -> {}", rowNumber, productName, categoryCode);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(String.format(
                            "행 %d: 추천된 카테고리 코드를 숫자로 변환할 수 없습니다. (값: '%s')", 
                            rowNumber, predictedCode));
                    }
                } else {
                    throw new IllegalArgumentException(String.format(
                        "행 %d: 카테고리 코드를 찾을 수 없습니다. (입력값: '%s') " +
                        "다음 중 하나를 시도해주세요:\n" +
                        "1. 쿠팡 카테고리 코드(숫자)를 직접 입력\n" +
                        "2. 정확한 한글 카테고리 경로 입력 (예: '패션의류잡화 > 여성패션 > 여성의류 > 티셔츠')\n" +
                        "3. 상품명을 더 상세하게 입력하여 자동 추천 활용", 
                        rowNumber, trimmed));
                }
            }
        }
        
        productData.put("displayCategoryCode", categoryCode);
        
        // 필수 필드: sellerProductName (상품명)
        String productName = getStringValue(rowData, "상품명");
        if (productName == null || productName.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 상품명은 필수입니다.", rowNumber));
        }
        productData.put("sellerProductName", productName.trim());
        
        // 필수 필드: saleStartedAt, saleEndedAt (판매 기간)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusYears(1); // 기본값: 1년 후
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        productData.put("saleStartedAt", now.format(formatter));
        productData.put("saleEndedAt", endDate.format(formatter));
        
        // 필수 필드: items (옵션 목록)
        List<Map<String, Object>> items = createItems(rowData, rowNumber, categoryCode);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 최소 하나의 옵션(item)이 필요합니다.", rowNumber));
        }
        productData.put("items", items);
        
        // 필수 필드: vendorUserId (실사용자 아이디 - Wing 사용자)
        // vendorUserId는 vendorId와 다를 수 있으므로 설정에서 가져옴
        // 설정에 없으면 vendorId를 사용 (하위 호환성)
        String vendorUserId = coupangProperties.getVendorUserId();
        if (vendorUserId == null || vendorUserId.trim().isEmpty()) {
            vendorUserId = vendorId; // 설정에 없으면 vendorId 사용
            log.warn("행 {}: vendorUserId가 설정되지 않아 vendorId를 사용합니다. vendorUserId를 설정 파일에 추가하는 것을 권장합니다.", rowNumber);
        }
        productData.put("vendorUserId", vendorUserId);
        
        // 필수 필드: requested (자동승인요청여부, 기본값: true)
        productData.put("requested", true);
        
        // 선택 필드: brand (브랜드)
        String brand = getStringValue(rowData, "브랜드");
        if (brand != null && !brand.trim().isEmpty()) {
            productData.put("brand", brand.trim());
        }
        
        // 선택 필드: displayProductName (전시 상품명)
        productData.put("displayProductName", productName.trim());
        
        // 선택 필드: extra (추가 정보)
        Map<String, Object> extra = new HashMap<>();
        
        String manufacturer = getStringValue(rowData, "제조사");
        if (manufacturer != null && !manufacturer.trim().isEmpty()) {
            extra.put("manufacturer", manufacturer.trim());
        }
        
        String modelName = getStringValue(rowData, "모델명");
        if (modelName != null && !modelName.trim().isEmpty()) {
            extra.put("modelName", modelName.trim());
        }
        
        String barcode = getStringValue(rowData, "바코드");
        if (barcode != null && !barcode.trim().isEmpty()) {
            extra.put("barcode", barcode.trim());
        }
        
        String originArea = getStringValue(rowData, "원산지");
        if (originArea != null && !originArea.trim().isEmpty()) {
            extra.put("originArea", originArea.trim());
        }
        
        if (!extra.isEmpty()) {
            productData.put("extra", extra);
        }
        
        // 필수 필드: 배송 정보 (API 문서 기준 - 상품 레벨 필드)
        // deliveryMethod: "SEQUENCIAL" (일반배송), "COLD_FRESH" (신선냉동), "MAKE_ORDER" (주문제작), "AGENT_BUY" (구매대행), "VENDOR_DIRECT" (설치배송)
        productData.put("deliveryMethod", "SEQUENCIAL");
        
        // deliveryCompanyCode: 택배사 코드 (필수)
        productData.put("deliveryCompanyCode", "KDEXP"); // 기본값: 경동택배 (실제 운영 시에는 설정 파일에서 가져와야 함)
        
        // deliveryChargeType: "FREE" (무료배송), "NOT_FREE" (유료배송), "CHARGE_RECEIVED" (착불배송), "CONDITIONAL_FREE" (조건부 무료배송)
        productData.put("deliveryChargeType", "FREE");
        
        // deliveryCharge: 기본배송비 (유료배송 또는 조건부 무료배송 시)
        productData.put("deliveryCharge", 0);
        
        // freeShipOverAmount: 무료배송을 위한 조건 금액
        productData.put("freeShipOverAmount", 0);
        
        // deliveryChargeOnReturn: 초도반품배송비 (무료배송인 경우 반품시 소비자가 지불하는 배송비)
        productData.put("deliveryChargeOnReturn", 2500);
        
        // remoteAreaDeliverable: "Y" (도서산간 배송), "N" (도서산간 배송안함)
        productData.put("remoteAreaDeliverable", "N");
        
        // unionDeliveryType: "UNION_DELIVERY" (묶음 배송 가능), "NOT_UNION_DELIVERY" (묶음 배송 불가능)
        productData.put("unionDeliveryType", "NOT_UNION_DELIVERY");
        
        // 필수 필드: 반품 정보 (API 문서 기준 - 상품 레벨 필드)
        // returnCenterCode: 반품지센터코드 (필수) - 실제 운영 시에는 반품지 조회 API로 가져와야 함
        productData.put("returnCenterCode", "NO_RETURN_CENTERCODE"); // 반품지가 없는 경우
        
        // returnChargeName: 반품지명
        productData.put("returnChargeName", "기본반품지");
        
        // companyContactNumber: 반품지연락처 (필수)
        productData.put("companyContactNumber", "010-0000-0000");
        
        // returnZipCode: 반품지우편번호
        productData.put("returnZipCode", "00000");
        
        // returnAddress: 반품지주소
        productData.put("returnAddress", "서울특별시 강남구");
        
        // returnAddressDetail: 반품지주소상세 (최소 1자 이상 필수)
        productData.put("returnAddressDetail", "상세주소");
        
        // returnCharge: 반품배송비
        productData.put("returnCharge", 2500);
        
        // outboundShippingPlaceCode: 출고지주소코드 (필수)
        Long outboundShippingPlaceCode = getOutboundShippingPlaceCode(rowNumber);
        productData.put("outboundShippingPlaceCode", outboundShippingPlaceCode);
        
        // 필수 필드: 이미지 (상품 이미지)
        List<String> productImages = new ArrayList<>();
        String mainImageUrl = getStringValue(rowData, "대표이미지URL");
        if (mainImageUrl != null && !mainImageUrl.trim().isEmpty()) {
            productImages.add(mainImageUrl.trim());
        }
        String additionalImageUrl1 = getStringValue(rowData, "추가이미지URL1");
        if (additionalImageUrl1 != null && !additionalImageUrl1.trim().isEmpty()) {
            productImages.add(additionalImageUrl1.trim());
        }
        if (productImages.isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 대표이미지URL은 필수입니다.", rowNumber));
        }
        productData.put("productImageUrls", productImages);
        
        // 필수 필드: 상세 설명
        String detailContent = getStringValue(rowData, "상세설명");
        if (detailContent == null || detailContent.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 상세설명은 필수입니다.", rowNumber));
        }
        productData.put("detailContent", detailContent.trim());
        
        // 선택 필드: requested (승인 요청 여부, 기본값: true)
        productData.put("requested", true);
        
        log.debug("엑셀 행 {} 쿠팡 형식 변환 완료: 상품명={}", rowNumber, productName);
        
        return productData;
    }
    
    /**
     * items 배열 생성 (옵션 정보 포함)
     */
    private List<Map<String, Object>> createItems(Map<String, Object> rowData, Integer rowNumber, Long categoryCode) {
        List<Map<String, Object>> items = new ArrayList<>();
        
        // 필수 필드: 판매가
        BigDecimal salePrice = getBigDecimalValue(rowData, "판매가");
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(String.format("행 %d: 판매가는 필수이며 0보다 커야 합니다.", rowNumber));
        }
        
        // 필수 필드: 재고수량
        Integer stockQuantity = getIntegerValue(rowData, "재고수량");
        if (stockQuantity == null || stockQuantity < 0) {
            throw new IllegalArgumentException(String.format("행 %d: 재고수량은 필수이며 0 이상이어야 합니다.", rowNumber));
        }
        
        // 옵션 정보 수집
        String optionName1 = getStringValue(rowData, "옵션명1");
        String optionValue1 = getStringValue(rowData, "옵션값1");
        String optionName2 = getStringValue(rowData, "옵션명2");
        String optionValue2 = getStringValue(rowData, "옵션값2");
        
        log.info("행 {}: 옵션 정보 수집 - 옵션명1={}, 옵션값1={}, 옵션명2={}, 옵션값2={}, 카테고리={}", 
                rowNumber, optionName1, optionValue1, optionName2, optionValue2, categoryCode);
        
        // 카테고리 메타데이터에서 허용된 옵션명 목록 및 필수 옵션 확인
        Map<String, Object> categoryOptionInfo = getCategoryOptionInfo(categoryCode, rowNumber);
        Set<String> allowedOptionNames = (Set<String>) categoryOptionInfo.get("allowedOptionNames");
        Set<String> requiredOptionNames = (Set<String>) categoryOptionInfo.get("requiredOptionNames");
        
        log.info("행 {}: 카테고리 {} 허용된 옵션명 목록: {}", rowNumber, categoryCode, allowedOptionNames);
        log.info("행 {}: 카테고리 {} 필수 옵션명 목록: {}", rowNumber, categoryCode, requiredOptionNames);
        
        // 입력된 옵션을 Map으로 수집 (옵션명 -> 옵션값)
        Map<String, String> inputOptions = new HashMap<>();
        if (optionName1 != null && !optionName1.trim().isEmpty() && 
            optionValue1 != null && !optionValue1.trim().isEmpty()) {
            inputOptions.put(optionName1.trim(), optionValue1.trim());
        }
        if (optionName2 != null && !optionName2.trim().isEmpty() && 
            optionValue2 != null && !optionValue2.trim().isEmpty()) {
            inputOptions.put(optionName2.trim(), optionValue2.trim());
        }
        
        // 필수 옵션이 누락되었는지 확인
        if (requiredOptionNames != null && !requiredOptionNames.isEmpty()) {
            Set<String> missingRequiredOptions = new HashSet<>(requiredOptionNames);
            missingRequiredOptions.removeAll(inputOptions.keySet());
            
            if (!missingRequiredOptions.isEmpty()) {
                String errorMessage = String.format(
                    "행 %d: 카테고리 %d에 필수 옵션이 누락되었습니다. " +
                    "필수 옵션: %s. " +
                    "엑셀에 다음 컬럼을 추가해주세요: 옵션명3, 옵션값3, 옵션명4, 옵션값4 등",
                    rowNumber, categoryCode, missingRequiredOptions);
                log.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }
        }
        
        // 허용된 옵션만 필터링
        List<String> validOptionNames = new ArrayList<>();
        List<String> validOptionValues = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : inputOptions.entrySet()) {
            String optionName = entry.getKey();
            String optionValue = entry.getValue();
            
            if (allowedOptionNames == null || allowedOptionNames.isEmpty() || allowedOptionNames.contains(optionName)) {
                validOptionNames.add(optionName);
                validOptionValues.add(optionValue);
                log.info("행 {}: 옵션명 '{}' 허용됨", rowNumber, optionName);
            } else {
                log.warn("행 {}: 옵션명 '{}'는 카테고리 {}에서 허용되지 않습니다. 제외합니다. (허용된 옵션: {})", 
                        rowNumber, optionName, categoryCode, allowedOptionNames);
            }
        }
        
        // 필수 옵션이 모두 포함되었는지 다시 확인
        if (requiredOptionNames != null && !requiredOptionNames.isEmpty()) {
            Set<String> validOptionNamesSet = new HashSet<>(validOptionNames);
            Set<String> missingRequiredOptions = new HashSet<>(requiredOptionNames);
            missingRequiredOptions.removeAll(validOptionNamesSet);
            
            if (!missingRequiredOptions.isEmpty()) {
                String errorMessage = String.format(
                    "행 %d: 카테고리 %d에 필수 옵션이 누락되었습니다. " +
                    "필수 옵션: %s. " +
                    "엑셀에 다음 컬럼을 추가해주세요: 옵션명3, 옵션값3, 옵션명4, 옵션값4 등",
                    rowNumber, categoryCode, missingRequiredOptions);
                log.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }
        }
        
        // 유효한 옵션이 있는 경우 옵션 포함하여 item 생성
        if (!validOptionNames.isEmpty() && validOptionNames.size() == validOptionValues.size()) {
            log.info("행 {}: 옵션 포함하여 상품 등록 - 옵션명: {}, 옵션값: {}", rowNumber, validOptionNames, validOptionValues);
            Map<String, Object> item = createItem(rowData, rowNumber, salePrice, stockQuantity, validOptionNames, validOptionValues, categoryCode);
            items.add(item);
        } else {
            // 옵션이 없거나 모두 제외된 경우
            if (requiredOptionNames != null && !requiredOptionNames.isEmpty()) {
                // 필수 옵션이 있는데 옵션이 없으면 에러
                String errorMessage = String.format(
                    "행 %d: 카테고리 %d에 필수 옵션이 필요합니다. " +
                    "필수 옵션: %s. " +
                    "엑셀에 다음 컬럼을 추가해주세요: 옵션명1, 옵션값1, 옵션명2, 옵션값2 등",
                    rowNumber, categoryCode, requiredOptionNames);
                log.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }
            
            // 필수 옵션이 없는 경우 옵션 없이 등록
            if ((optionName1 != null && !optionName1.trim().isEmpty()) || 
                (optionName2 != null && !optionName2.trim().isEmpty())) {
                log.warn("행 {}: 입력된 옵션이 카테고리 {}에서 허용되지 않아 옵션 없이 상품을 등록합니다.", rowNumber, categoryCode);
            }
            Map<String, Object> item = createItem(rowData, rowNumber, salePrice, stockQuantity, null, null, categoryCode);
            items.add(item);
        }
        
        return items;
    }
    
    /**
     * item 객체 생성 (필수 필드 포함)
     */
    private Map<String, Object> createItem(Map<String, Object> rowData, Integer rowNumber,
                                           BigDecimal salePrice, Integer stockQuantity,
                                           List<String> optionNames, List<String> optionValues,
                                           Long categoryCode) {
        Map<String, Object> item = new HashMap<>();
        
        // itemName 생성
        String productName = getStringValue(rowData, "상품명");
        String itemName = productName != null ? productName : "";
        if (optionValues != null && !optionValues.isEmpty() && optionNames != null && !optionNames.isEmpty()) {
            itemName += " - " + String.join(" / ", optionValues);
        }
        item.put("itemName", itemName.trim());
        
        // 가격 정보
        item.put("originalPrice", salePrice.intValue());
        item.put("salePrice", salePrice.intValue());
        item.put("stockQuantity", stockQuantity);
        
        // 필수 필드: attributes (옵션이 있는 경우)
        if (optionNames != null && !optionNames.isEmpty() && optionValues != null && optionValues.size() == optionNames.size()) {
            List<Map<String, Object>> attributes = new ArrayList<>();
            for (int i = 0; i < optionNames.size(); i++) {
                Map<String, Object> attribute = new HashMap<>();
                attribute.put("attributeTypeName", optionNames.get(i));
                attribute.put("attributeValueName", optionValues.get(i));
                attributes.add(attribute);
            }
            item.put("attributes", attributes);
        } else {
            // 옵션이 없어도 빈 배열 필요할 수 있음
            item.put("attributes", new ArrayList<>());
        }
        
        // 필수 필드: 기준출고일 (일반적으로 1일)
        // API 문서: outboundShippingTimeDay (Number)
        item.put("outboundShippingTimeDay", 1);
        
        // 필수 필드: 인당최대구매수량 (기본값: 0 = 제한 없음)
        // API 문서: maximumBuyForPerson (Number)
        item.put("maximumBuyForPerson", 0);
        
        // 필수 필드: 최대구매수량기간 (일반적으로 1일)
        // API 문서: maximumBuyForPersonPeriod (Number)
        item.put("maximumBuyForPersonPeriod", 1);
        
        // 필수 필드: 판매가능수량 (재고수량과 동일)
        // API 문서: maximumBuyCount (Number)
        item.put("maximumBuyCount", stockQuantity);
        
        // 필수 필드: 과세여부 (기본값: TAX - 과세)
        item.put("taxType", "TAX");
        
        // 필수 필드: 이미지 (item별 이미지, 객체 배열 형식)
        // 쿠팡 API는 vendorPath(업체이미지경로), imageType(이미지 타입), imageOrder(이미지 순서) 필수
        List<Map<String, Object>> itemImages = new ArrayList<>();
        String mainImageUrl = getStringValue(rowData, "대표이미지URL");
        if (mainImageUrl != null && !mainImageUrl.trim().isEmpty()) {
            Map<String, Object> imageObj = new HashMap<>();
            imageObj.put("vendorPath", mainImageUrl.trim()); // 업체이미지경로
            imageObj.put("imageType", "REPRESENTATION"); // 대표 이미지
            imageObj.put("imageOrder", 1); // 이미지 순서
            itemImages.add(imageObj);
        }
        String additionalImageUrl1 = getStringValue(rowData, "추가이미지URL1");
        if (additionalImageUrl1 != null && !additionalImageUrl1.trim().isEmpty()) {
            Map<String, Object> imageObj = new HashMap<>();
            imageObj.put("vendorPath", additionalImageUrl1.trim()); // 업체이미지경로
            imageObj.put("imageType", "DETAIL"); // 상세 이미지
            imageObj.put("imageOrder", 2); // 이미지 순서
            itemImages.add(imageObj);
        }
        // 최소 2개 이미지 필요 (에러 메시지에서 1번, 2번 이미지 요구)
        if (itemImages.size() < 2) {
            // 2번째 이미지가 없으면 대표 이미지를 복사하여 추가
            if (mainImageUrl != null && !mainImageUrl.trim().isEmpty()) {
                Map<String, Object> imageObj2 = new HashMap<>();
                imageObj2.put("vendorPath", mainImageUrl.trim());
                imageObj2.put("imageType", "DETAIL");
                imageObj2.put("imageOrder", 2);
                itemImages.add(imageObj2);
            }
        }
        if (itemImages.isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 대표이미지URL은 필수입니다.", rowNumber));
        }
        item.put("images", itemImages);
        
        // 필수 필드: 단위수량 (기본값: 1)
        // API 문서: unitCount (Number)
        item.put("unitCount", 1);
        
        // 필수 필드: 고시정보 (카테고리별로 필수 고시정보가 다를 수 있음)
        // 카테고리 메타데이터 API를 통해 해당 카테고리의 필수 고시정보를 동적으로 가져옴
        List<Map<String, Object>> notices = getNoticesForCategory(categoryCode, rowNumber);
        
        // notices가 비어있으면 빈 배열로 설정 (일부 카테고리는 고시정보가 필요 없을 수 있음)
        // 하지만 쿠팡 API는 빈 배열도 허용하므로, 빈 배열로 설정
        if (notices == null) {
            notices = new ArrayList<>();
        }
        item.put("notices", notices);
        
        // 필수 필드: 해외구매대행여부 (기본값: NOT_OVERSEAS_PURCHASED)
        // API 문서: overseasPurchased (String) - "OVERSEAS_PURCHASED" 또는 "NOT_OVERSEAS_PURCHASED"
        item.put("overseasPurchased", "NOT_OVERSEAS_PURCHASED");
        
        // 필수 필드: 컨텐츠 (상세설명 사용, 객체 배열 형식)
        // 쿠팡 API는 contentsType과 contentDetails(배열) 필수
        // contentDetails는 배열이고, 각 요소는 content와 detailType을 가진 객체
        List<Map<String, Object>> contents = new ArrayList<>();
        String detailContent = getStringValue(rowData, "상세설명");
        if (detailContent != null && !detailContent.trim().isEmpty()) {
            Map<String, Object> contentObj = new HashMap<>();
            contentObj.put("contentsType", "TEXT"); // 컨텐츠 타입 (TEXT, HTML 등)
            
            // contentDetails는 배열이어야 함
            List<Map<String, Object>> contentDetailsList = new ArrayList<>();
            Map<String, Object> contentDetail = new HashMap<>();
            contentDetail.put("content", detailContent.trim()); // 상세 내용
            contentDetail.put("detailType", "TEXT"); // 세부 타입 (TEXT, IMAGE 등)
            contentDetailsList.add(contentDetail);
            
            contentObj.put("contentDetails", contentDetailsList);
            contents.add(contentObj);
        } else {
            // 빈 컨텐츠라도 배열 형식으로 전송 (필수)
            Map<String, Object> contentObj = new HashMap<>();
            contentObj.put("contentsType", "TEXT");
            
            List<Map<String, Object>> contentDetailsList = new ArrayList<>();
            Map<String, Object> contentDetail = new HashMap<>();
            contentDetail.put("content", "");
            contentDetail.put("detailType", "TEXT");
            contentDetailsList.add(contentDetail);
            
            contentObj.put("contentDetails", contentDetailsList);
            contents.add(contentObj);
        }
        item.put("contents", contents);
        
        // 필수 필드: 성인여부 (기본값: EVERYONE)
        // API 문서: adultOnly (String) - "ADULT_ONLY" 또는 "EVERYONE"
        item.put("adultOnly", "EVERYONE");
        
        return item;
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
    /**
     * 카테고리별 고시정보 가져오기
     * 카테고리 메타데이터 API를 통해 해당 카테고리의 필수 고시정보를 조회
     * 
     * API 문서 기준 응답 구조:
     * {
     *   "code": "SUCCESS",
     *   "data": {
     *     "noticeCategories": [
     *       {
     *         "noticeCategoryName": "자동차용품",
     *         "noticeCategoryDetailNames": [
     *           {
     *             "noticeCategoryDetailName": "품명 및 모델명",
     *             "required": "MANDATORY"
     *           }
     *         ]
     *       }
     *     ]
     *   }
     * }
     * 
     * @param categoryCode 카테고리 코드
     * @param rowNumber 행 번호 (로깅용)
     * @return 고시정보 리스트
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getNoticesForCategory(Long categoryCode, Integer rowNumber) {
        List<Map<String, Object>> notices = new ArrayList<>();
        
        if (categoryCode == null) {
            log.warn("행 {}: 카테고리 코드가 없어 고시정보를 가져올 수 없습니다.", rowNumber);
            return notices;
        }
        
        try {
            // 카테고리 메타데이터 API 호출
            Map<String, Object> metadataResponse = coupangApiClient.getCategoryMetadata(categoryCode).block();
            
            log.debug("행 {}: 카테고리 메타데이터 응답: {}", rowNumber, metadataResponse);
            
            if (metadataResponse != null) {
                // 응답 코드 확인 (SUCCESS 또는 200)
                Object codeObj = metadataResponse.get("code");
                boolean isSuccess = "SUCCESS".equals(String.valueOf(codeObj)) || 
                                  "200".equals(String.valueOf(codeObj)) ||
                                  (codeObj instanceof Number && ((Number) codeObj).intValue() == 200);
                
                if (isSuccess) {
                    Map<String, Object> data = (Map<String, Object>) metadataResponse.get("data");
                    if (data != null) {
                        log.debug("행 {}: 카테고리 메타데이터 data 키들: {}", rowNumber, data.keySet());
                        log.debug("행 {}: 카테고리 메타데이터 data 전체: {}", rowNumber, data);
                        
                        // requiredNotices 필드 확인 (직접 필수 고시정보가 제공되는 경우)
                        if (data.containsKey("requiredNotices")) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> requiredNotices = (List<Map<String, Object>>) data.get("requiredNotices");
                            if (requiredNotices != null && !requiredNotices.isEmpty()) {
                                log.info("행 {}: 카테고리 {}의 requiredNotices {}개 발견", rowNumber, categoryCode, requiredNotices.size());
                                
                                // requiredNotices를 직접 사용
                                // requiredNotices는 이미 하나의 noticeCategoryName으로 그룹화되어 있을 가능성이 높음
                                for (Map<String, Object> requiredNotice : requiredNotices) {
                                    Map<String, Object> notice = new HashMap<>();
                                    
                                    Object noticeCategoryNameObj = requiredNotice.get("noticeCategoryName");
                                    Object noticeCategoryDetailNameObj = requiredNotice.get("noticeCategoryDetailName");
                                    
                                    if (noticeCategoryNameObj != null && noticeCategoryDetailNameObj != null) {
                                        notice.put("noticeCategoryName", String.valueOf(noticeCategoryNameObj));
                                        notice.put("noticeCategoryDetailName", String.valueOf(noticeCategoryDetailNameObj));
                                        notice.put("content", "상세페이지 참조"); // 기본값
                                        notices.add(notice);
                                        
                                        log.debug("행 {}: 필수 고시정보 추가 (requiredNotices): {} > {}", 
                                                rowNumber, noticeCategoryNameObj, noticeCategoryDetailNameObj);
                                    }
                                }
                                
                                // requiredNotices가 있으면 그것만 사용하고 noticeCategories는 무시
                                log.info("행 {}: 카테고리 {}의 필수 고시정보 {}개를 가져왔습니다. (requiredNotices 사용)", rowNumber, categoryCode, notices.size());
                            }
                        }
                        
                        // requiredNotices가 없거나 비어있으면 noticeCategories 사용
                        if (notices.isEmpty()) {
                            // noticeCategories 배열 추출 (API 문서 기준)
                            List<Map<String, Object>> noticeCategories = (List<Map<String, Object>>) data.get("noticeCategories");
                            
                            if (noticeCategories != null && !noticeCategories.isEmpty()) {
                                log.info("행 {}: 카테고리 {}의 고시정보 카테고리 {}개 발견", rowNumber, categoryCode, noticeCategories.size());
                                
                                // 중요: 쿠팡 API는 하나의 noticeCategoryName만 허용합니다.
                                // 첫 번째 noticeCategory의 필수(MANDATORY) 항목만 사용합니다.
                                Map<String, Object> firstNoticeCategory = noticeCategories.get(0);
                                String noticeCategoryName = String.valueOf(firstNoticeCategory.get("noticeCategoryName"));
                                
                                log.info("행 {}: 첫 번째 고시정보 카테고리 선택: '{}' (총 {}개 중)", 
                                        rowNumber, noticeCategoryName, noticeCategories.size());
                                
                                // noticeCategoryDetailNames 배열 추출
                                List<Map<String, Object>> detailNames = (List<Map<String, Object>>) firstNoticeCategory.get("noticeCategoryDetailNames");
                                
                                if (detailNames != null && !detailNames.isEmpty()) {
                                    // 필수(MANDATORY) 고시정보 모두에 대해 notice 생성
                                    for (Map<String, Object> detailName : detailNames) {
                                        String required = String.valueOf(detailName.get("required"));
                                        
                                        if ("MANDATORY".equals(required)) {
                                            String noticeCategoryDetailName = String.valueOf(detailName.get("noticeCategoryDetailName"));
                                            
                                            Map<String, Object> notice = new HashMap<>();
                                            notice.put("noticeCategoryName", noticeCategoryName);
                                            notice.put("noticeCategoryDetailName", noticeCategoryDetailName);
                                            notice.put("content", "상세페이지 참조"); // 기본값
                                            notices.add(notice);
                                            
                                            log.debug("행 {}: 필수 고시정보 추가: {} > {}", rowNumber, noticeCategoryName, noticeCategoryDetailName);
                                        }
                                    }
                                }
                                
                                log.info("행 {}: 카테고리 {}의 필수 고시정보 {}개를 가져왔습니다. (noticeCategory: '{}')", 
                                        rowNumber, categoryCode, notices.size(), noticeCategoryName);
                            } else {
                                log.warn("행 {}: 카테고리 {}에 고시정보 카테고리가 없습니다. (data: {})", rowNumber, categoryCode, data);
                                // 고시정보가 없어도 빈 배열로 반환 (일부 카테고리는 고시정보가 필요 없을 수 있음)
                            }
                        }
                    } else {
                        log.warn("행 {}: 카테고리 메타데이터 응답의 data가 null입니다.", rowNumber);
                    }
                } else {
                    log.warn("행 {}: 카테고리 메타데이터 조회 실패: code={}, message={}", 
                            rowNumber, codeObj, metadataResponse.get("message"));
                }
            } else {
                log.warn("행 {}: 카테고리 메타데이터 조회 응답이 null입니다.", rowNumber);
            }
        } catch (Exception e) {
            log.error("행 {}: 카테고리 메타데이터 조회 중 오류 발생 (카테고리: {}): {}", rowNumber, categoryCode, e.getMessage(), e);
            // 에러가 발생해도 빈 배열로 계속 진행
        }
        
        return notices;
    }
    
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
                str = str.replace(",", "");
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                log.warn("행 {}: {} 값을 BigDecimal로 변환 실패: {}", rowData.get("_rowNumber"), key, value);
                return null;
            }
        }
        return null;
    }
    
    /**
     * 카테고리에서 옵션 정보 조회 (허용된 옵션명 및 필수 옵션명)
     * 쿠팡 API는 카테고리 메타데이터에 정의된 옵션만 허용합니다.
     * 
     * @param categoryCode 카테고리 코드
     * @param rowNumber 행 번호 (로깅용)
     * @return Map containing "allowedOptionNames" (Set<String>) and "requiredOptionNames" (Set<String>)
     */
    private Map<String, Object> getCategoryOptionInfo(Long categoryCode, Integer rowNumber) {
        Map<String, Object> result = new HashMap<>();
        Set<String> allowedOptionNames = new HashSet<>();
        Set<String> requiredOptionNames = new HashSet<>();
        
        if (categoryCode == null) {
            log.debug("행 {}: 카테고리 코드가 없어 모든 옵션 허용", rowNumber);
            result.put("allowedOptionNames", null);
            result.put("requiredOptionNames", new HashSet<>());
            return result;
        }
        
        try {
            log.info("행 {}: 카테고리 {} 메타데이터 조회 시작...", rowNumber, categoryCode);
            // 카테고리 메타데이터 조회
            Map<String, Object> metadataResponse = coupangApiClient.getCategoryMetadata(categoryCode).block();
            
            if (metadataResponse != null) {
                Object codeObj = metadataResponse.get("code");
                boolean isSuccess = "SUCCESS".equals(String.valueOf(codeObj)) || 
                                  "200".equals(String.valueOf(codeObj)) ||
                                  (codeObj instanceof Number && ((Number) codeObj).intValue() == 200);
                
                log.info("행 {}: 카테고리 {} 메타데이터 조회 결과 - code={}, success={}", 
                        rowNumber, categoryCode, codeObj, isSuccess);
                
                if (isSuccess && metadataResponse.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) metadataResponse.get("data");
                    
                    if (data != null) {
                        log.debug("행 {}: 카테고리 {} 메타데이터 data 키들: {}", rowNumber, categoryCode, data.keySet());
                        
                        // optionGuides 필드 확인 (옵션 가이드 목록)
                        if (data.containsKey("optionGuides")) {
                            Object optionGuides = data.get("optionGuides");
                            log.info("행 {}: 카테고리 {} optionGuides 필드 발견: {}", rowNumber, categoryCode, optionGuides);
                            
                            if (optionGuides instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> guides = (List<Map<String, Object>>) optionGuides;
                                
                                if (guides != null && !guides.isEmpty()) {
                                    log.info("행 {}: 카테고리 {} 옵션 가이드 {}개 발견", rowNumber, categoryCode, guides.size());
                                    
                                    for (Map<String, Object> guide : guides) {
                                        Object optionName = guide.get("optionName");
                                        Object required = guide.get("required");
                                        
                                        if (optionName != null) {
                                            String optionNameStr = String.valueOf(optionName);
                                            allowedOptionNames.add(optionNameStr);
                                            
                                            // 필수 옵션 확인
                                            if (required != null) {
                                                boolean isRequired = "MANDATORY".equals(String.valueOf(required)) ||
                                                                     "true".equalsIgnoreCase(String.valueOf(required)) ||
                                                                     (required instanceof Boolean && (Boolean) required);
                                                if (isRequired) {
                                                    requiredOptionNames.add(optionNameStr);
                                                    log.info("행 {}: 필수 옵션 발견: {}", rowNumber, optionNameStr);
                                                }
                                            }
                                            
                                            log.debug("행 {}: 허용된 옵션명 추가: {} (필수: {})", rowNumber, optionNameStr, required);
                                        }
                                    }
                                    
                                    log.info("행 {}: 카테고리 {} 허용된 옵션명 {}개: {}", 
                                            rowNumber, categoryCode, allowedOptionNames.size(), allowedOptionNames);
                                    log.info("행 {}: 카테고리 {} 필수 옵션명 {}개: {}", 
                                            rowNumber, categoryCode, requiredOptionNames.size(), requiredOptionNames);
                                    
                                    result.put("allowedOptionNames", allowedOptionNames.isEmpty() ? null : allowedOptionNames);
                                    result.put("requiredOptionNames", requiredOptionNames);
                                    return result;
                                } else {
                                    log.info("행 {}: 카테고리 {} 옵션 가이드가 비어있음 - 옵션 불허", rowNumber, categoryCode);
                                    result.put("allowedOptionNames", new HashSet<>());
                                    result.put("requiredOptionNames", new HashSet<>());
                                    return result;
                                }
                            }
                        }
                        
                        // attributes 필드 확인 (대안)
                        if (data.containsKey("attributes")) {
                            Object attributes = data.get("attributes");
                            log.info("행 {}: 카테고리 {} attributes 필드 발견: {}", rowNumber, categoryCode, attributes);
                            
                            if (attributes instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> attrs = (List<Map<String, Object>>) attributes;
                                
                                if (attrs != null && !attrs.isEmpty()) {
                                    for (Map<String, Object> attr : attrs) {
                                        Object attributeTypeName = attr.get("attributeTypeName");
                                        Object required = attr.get("required");
                                        
                                        if (attributeTypeName != null) {
                                            String attrName = String.valueOf(attributeTypeName);
                                            allowedOptionNames.add(attrName);
                                            
                                            // 필수 옵션 확인
                                            if (required != null) {
                                                boolean isRequired = "MANDATORY".equals(String.valueOf(required)) ||
                                                                     "true".equalsIgnoreCase(String.valueOf(required)) ||
                                                                     (required instanceof Boolean && (Boolean) required);
                                                if (isRequired) {
                                                    requiredOptionNames.add(attrName);
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (!allowedOptionNames.isEmpty()) {
                                        log.info("행 {}: 카테고리 {} attributes에서 허용된 옵션명 {}개: {}", 
                                                rowNumber, categoryCode, allowedOptionNames.size(), allowedOptionNames);
                                        log.info("행 {}: 카테고리 {} 필수 옵션명 {}개: {}", 
                                                rowNumber, categoryCode, requiredOptionNames.size(), requiredOptionNames);
                                        result.put("allowedOptionNames", allowedOptionNames);
                                        result.put("requiredOptionNames", requiredOptionNames);
                                        return result;
                                    }
                                }
                            }
                        }
                        
                        // useOptionYn 필드 확인 (옵션 사용 가능 여부)
                        if (data.containsKey("useOptionYn")) {
                            Object useOptionYn = data.get("useOptionYn");
                            log.info("행 {}: 카테고리 {} useOptionYn 필드 발견: {}", rowNumber, categoryCode, useOptionYn);
                            
                            boolean allowed = false;
                            if (useOptionYn instanceof Boolean) {
                                allowed = (Boolean) useOptionYn;
                            } else if (useOptionYn instanceof String) {
                                allowed = "Y".equalsIgnoreCase((String) useOptionYn) || 
                                         "true".equalsIgnoreCase((String) useOptionYn);
                            }
                            
                            if (!allowed) {
                                log.info("행 {}: 카테고리 {} 옵션 사용 불가 (useOptionYn=false) - 옵션 불허", rowNumber, categoryCode);
                                result.put("allowedOptionNames", new HashSet<>());
                                result.put("requiredOptionNames", new HashSet<>());
                                return result;
                            }
                        }
                        
                        // 옵션 관련 정보가 없으면 null 반환 (모든 옵션 허용)
                        log.info("행 {}: 카테고리 {} 옵션 관련 정보 없음 - 모든 옵션 허용", rowNumber, categoryCode);
                        result.put("allowedOptionNames", null);
                        result.put("requiredOptionNames", new HashSet<>());
                        return result;
                    }
                } else {
                    log.warn("행 {}: 카테고리 {} 메타데이터 조회 실패 또는 data 없음 - 모든 옵션 허용", rowNumber, categoryCode);
                }
            }
        } catch (Exception e) {
            log.error("행 {}: 카테고리 {} 메타데이터 조회 중 오류 발생: {}", rowNumber, categoryCode, e.getMessage(), e);
        }
        
        // 기본값: null 반환 (모든 옵션 허용)
        log.warn("행 {}: 카테고리 {} 메타데이터 조회 실패, 기본적으로 모든 옵션 허용", rowNumber, categoryCode);
        result.put("allowedOptionNames", null);
        result.put("requiredOptionNames", new HashSet<>());
        return result;
    }
    
    /**
     * 카테고리에서 허용된 옵션명 목록 조회 (하위 호환성 유지)
     * 
     * @deprecated getAllowedOptionNames 대신 getCategoryOptionInfo 사용 권장
     */
    @Deprecated
    private Set<String> getAllowedOptionNames(Long categoryCode, Integer rowNumber) {
        if (categoryCode == null) {
            log.debug("행 {}: 카테고리 코드가 없어 모든 옵션 허용", rowNumber);
            return null; // null이면 모든 옵션 허용
        }
        
        try {
            log.info("행 {}: 카테고리 {} 메타데이터 조회 시작...", rowNumber, categoryCode);
            // 카테고리 메타데이터 조회
            Map<String, Object> metadataResponse = coupangApiClient.getCategoryMetadata(categoryCode).block();
            
            if (metadataResponse != null) {
                Object codeObj = metadataResponse.get("code");
                boolean isSuccess = "SUCCESS".equals(String.valueOf(codeObj)) || 
                                  "200".equals(String.valueOf(codeObj)) ||
                                  (codeObj instanceof Number && ((Number) codeObj).intValue() == 200);
                
                log.info("행 {}: 카테고리 {} 메타데이터 조회 결과 - code={}, success={}", 
                        rowNumber, categoryCode, codeObj, isSuccess);
                
                if (isSuccess && metadataResponse.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) metadataResponse.get("data");
                    
                    if (data != null) {
                        log.debug("행 {}: 카테고리 {} 메타데이터 data 키들: {}", rowNumber, categoryCode, data.keySet());
                        
                        Set<String> allowedNames = new HashSet<>();
                        
                        // optionGuides 필드 확인 (옵션 가이드 목록)
                        if (data.containsKey("optionGuides")) {
                            Object optionGuides = data.get("optionGuides");
                            log.info("행 {}: 카테고리 {} optionGuides 필드 발견: {}", rowNumber, categoryCode, optionGuides);
                            
                            if (optionGuides instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> guides = (List<Map<String, Object>>) optionGuides;
                                
                                if (guides != null && !guides.isEmpty()) {
                                    log.info("행 {}: 카테고리 {} 옵션 가이드 {}개 발견", rowNumber, categoryCode, guides.size());
                                    
                                    for (Map<String, Object> guide : guides) {
                                        Object optionName = guide.get("optionName");
                                        if (optionName != null) {
                                            allowedNames.add(String.valueOf(optionName));
                                            log.debug("행 {}: 허용된 옵션명 추가: {}", rowNumber, optionName);
                                        }
                                    }
                                    
                                    log.info("행 {}: 카테고리 {} 허용된 옵션명 {}개: {}", 
                                            rowNumber, categoryCode, allowedNames.size(), allowedNames);
                                    return allowedNames;
                                } else {
                                    log.info("행 {}: 카테고리 {} 옵션 가이드가 비어있음 - 옵션 불허", rowNumber, categoryCode);
                                    return new HashSet<>(); // 빈 Set이면 옵션 불허
                                }
                            }
                        }
                        
                        // attributes 필드 확인 (대안)
                        if (data.containsKey("attributes")) {
                            Object attributes = data.get("attributes");
                            log.info("행 {}: 카테고리 {} attributes 필드 발견: {}", rowNumber, categoryCode, attributes);
                            
                            if (attributes instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> attrs = (List<Map<String, Object>>) attributes;
                                
                                if (attrs != null && !attrs.isEmpty()) {
                                    for (Map<String, Object> attr : attrs) {
                                        Object attributeTypeName = attr.get("attributeTypeName");
                                        if (attributeTypeName != null) {
                                            allowedNames.add(String.valueOf(attributeTypeName));
                                        }
                                    }
                                    
                                    if (!allowedNames.isEmpty()) {
                                        log.info("행 {}: 카테고리 {} attributes에서 허용된 옵션명 {}개: {}", 
                                                rowNumber, categoryCode, allowedNames.size(), allowedNames);
                                        return allowedNames;
                                    }
                                }
                            }
                        }
                        
                        // useOptionYn 필드 확인 (옵션 사용 가능 여부)
                        if (data.containsKey("useOptionYn")) {
                            Object useOptionYn = data.get("useOptionYn");
                            log.info("행 {}: 카테고리 {} useOptionYn 필드 발견: {}", rowNumber, categoryCode, useOptionYn);
                            
                            boolean allowed = false;
                            if (useOptionYn instanceof Boolean) {
                                allowed = (Boolean) useOptionYn;
                            } else if (useOptionYn instanceof String) {
                                allowed = "Y".equalsIgnoreCase((String) useOptionYn) || 
                                         "true".equalsIgnoreCase((String) useOptionYn);
                            }
                            
                            if (!allowed) {
                                log.info("행 {}: 카테고리 {} 옵션 사용 불가 (useOptionYn=false) - 옵션 불허", rowNumber, categoryCode);
                                return new HashSet<>(); // 빈 Set이면 옵션 불허
                            }
                        }
                        
                        // 옵션 관련 정보가 없으면 null 반환 (모든 옵션 허용)
                        log.info("행 {}: 카테고리 {} 옵션 관련 정보 없음 - 모든 옵션 허용", rowNumber, categoryCode);
                        return null;
                    }
                } else {
                    log.warn("행 {}: 카테고리 {} 메타데이터 조회 실패 또는 data 없음 - 모든 옵션 허용", rowNumber, categoryCode);
                }
            }
        } catch (Exception e) {
            log.error("행 {}: 카테고리 {} 메타데이터 조회 중 오류 발생: {}", rowNumber, categoryCode, e.getMessage(), e);
        }
        
        // 기본값: null 반환 (모든 옵션 허용)
        log.warn("행 {}: 카테고리 {} 메타데이터 조회 실패, 기본적으로 모든 옵션 허용", rowNumber, categoryCode);
        return null;
    }
    
    /**
     * 출고지 주소 코드 조회
     * 1. 설정 파일에서 먼저 확인
     * 2. 없으면 캐시 확인
     * 3. 캐시도 없으면 API 호출하여 조회
     * 
     * @param rowNumber 행 번호 (에러 메시지용)
     * @return 출고지 주소 코드
     */
    private Long getOutboundShippingPlaceCode(Integer rowNumber) {
        // 1. 설정 파일에서 먼저 확인
        Long outboundShippingPlaceCode = coupangProperties.getOutboundShippingPlaceCode();
        if (outboundShippingPlaceCode != null && outboundShippingPlaceCode != 0) {
            log.debug("행 {}: 설정 파일에서 출고지 코드 사용: {}", rowNumber, outboundShippingPlaceCode);
            return outboundShippingPlaceCode;
        }
        
        // 2. 캐시 확인
        if (cachedOutboundShippingPlaceCode != null && cachedOutboundShippingPlaceCode != 0) {
            log.debug("행 {}: 캐시에서 출고지 코드 사용: {}", rowNumber, cachedOutboundShippingPlaceCode);
            return cachedOutboundShippingPlaceCode;
        }
        
        // 3. API 호출하여 조회
        try {
            log.info("행 {}: 출고지 코드가 설정되지 않아 API로 조회합니다...", rowNumber);
            Map<String, Object> response = coupangApiClient.getOutboundShippingCenters().block();
            
            if (response != null) {
                // 응답 코드 확인
                Object codeObj = response.get("code");
                boolean isSuccess = "SUCCESS".equals(String.valueOf(codeObj)) || 
                                  "200".equals(String.valueOf(codeObj)) ||
                                  (codeObj instanceof Number && ((Number) codeObj).intValue() == 200);
                
                if (isSuccess && response.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> centers = (List<Map<String, Object>>) response.get("data");
                    
                    if (centers != null && !centers.isEmpty()) {
                        // 첫 번째 출고지 코드 사용
                        Map<String, Object> firstCenter = centers.get(0);
                        Object codeObj2 = firstCenter.get("outboundShippingPlaceCode");
                        
                        if (codeObj2 != null) {
                            Long code;
                            if (codeObj2 instanceof Number) {
                                code = ((Number) codeObj2).longValue();
                            } else {
                                code = Long.parseLong(String.valueOf(codeObj2));
                            }
                            
                            // 캐시에 저장
                            cachedOutboundShippingPlaceCode = code;
                            
                            String centerName = String.valueOf(firstCenter.get("name"));
                            log.info("행 {}: 출고지 코드 조회 성공: {} (출고지명: {})", rowNumber, code, centerName);
                            
                            if (centers.size() > 1) {
                                log.info("행 {}: 총 {}개의 출고지가 있습니다. 첫 번째 출고지를 사용합니다.", rowNumber, centers.size());
                            }
                            
                            return code;
                        } else {
                            throw new IllegalArgumentException(String.format(
                                "행 %d: 출고지 코드(outboundShippingPlaceCode)가 응답에 없습니다.",
                                rowNumber));
                        }
                    } else {
                        throw new IllegalArgumentException(String.format(
                            "행 %d: 등록된 출고지가 없습니다. 쿠팡 판매자 센터(https://wing.coupang.com) > 판매자 주소록에서 출고지를 먼저 등록해주세요.",
                            rowNumber));
                    }
                } else {
                    String message = String.valueOf(response.get("message"));
                    throw new IllegalArgumentException(String.format(
                        "행 %d: 출고지 조회 실패: %s",
                        rowNumber, message != null ? message : "알 수 없는 오류"));
                }
            } else {
                throw new IllegalArgumentException(String.format(
                    "행 %d: 출고지 조회 응답이 null입니다.",
                    rowNumber));
            }
        } catch (IllegalArgumentException e) {
            // 이미 IllegalArgumentException이면 그대로 전달
            throw e;
        } catch (Exception e) {
            log.error("행 {}: 출고지 코드 조회 중 오류 발생", rowNumber, e);
            throw new IllegalArgumentException(String.format(
                "행 %d: 출고지 코드를 조회할 수 없습니다: %s. " +
                "다음 중 하나를 시도해주세요:\n" +
                "1. 쿠팡 판매자 센터에서 출고지를 먼저 등록\n" +
                "2. application-secret.properties 파일에 'coupang.api.outbound-shipping-place-code=출고지코드' 직접 설정",
                rowNumber, e.getMessage()));
        }
    }
}
