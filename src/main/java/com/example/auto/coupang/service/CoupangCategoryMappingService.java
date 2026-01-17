package com.example.auto.coupang.service;

import com.example.auto.coupang.client.CoupangApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 쿠팡 카테고리 경로를 쿠팡 API 카테고리 코드로 변환하는 서비스
 * 
 * 쿠팡 카테고리 조회 API를 사용하여 동적으로 카테고리 매핑을 생성합니다.
 * 첫 호출 시 API를 통해 전체 카테고리를 조회하고, 이후에는 캐시된 매핑을 사용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoupangCategoryMappingService {
    
    private final CoupangApiClient coupangApiClient;
    
    /**
     * 카테고리 경로 -> 카테고리 코드 매핑 캐시
     * 형식: "대분류 > 중분류 > 소분류" -> "카테고리코드" (Long을 String으로 저장)
     */
    private Map<String, String> categoryMappingCache = null;
    
    /**
     * 카테고리 조회 API를 통해 매핑 테이블 초기화
     * 첫 호출 시에만 API를 호출하고, 이후에는 캐시를 사용합니다.
     */
    private void initializeCategoryMapping() {
        if (categoryMappingCache != null) {
            return; // 이미 초기화됨
        }
        
        try {
            log.info("쿠팡 카테고리 매핑 초기화 시작: API에서 카테고리 조회...");
            
            // 카테고리 목록 조회
            Map<String, Object> response = coupangApiClient.getCategories().block();
            
            if (response != null && "SUCCESS".equals(response.get("code"))) {
                categoryMappingCache = new HashMap<>();
                
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                
                if (data != null) {
                    // 카테고리 트리 순회하며 매핑 생성
                    buildCategoryMapping(data, "");
                    
                    log.info("쿠팡 카테고리 매핑 초기화 완료: 총 {}개", categoryMappingCache.size());
                    
                    // 처음 10개만 로그 출력
                    int count = 0;
                    for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                        if (count++ < 10) {
                            log.info("카테고리 매핑: {} -> {}", entry.getKey(), entry.getValue());
                        }
                    }
                } else {
                    log.warn("카테고리 조회 결과의 data가 비어있습니다.");
                    initializeEmptyMapping();
                }
            } else {
                log.warn("카테고리 조회 실패: {}", response);
                initializeEmptyMapping();
            }
        } catch (Exception e) {
            log.error("카테고리 매핑 초기화 실패: {}", e.getMessage(), e);
            initializeEmptyMapping();
        }
    }
    
    /**
     * 카테고리 트리를 순회하며 매핑 테이블 생성
     * 
     * @param category 카테고리 객체
     * @param parentPath 부모 카테고리 경로
     */
    @SuppressWarnings("unchecked")
    private void buildCategoryMapping(Map<String, Object> category, String parentPath) {
        if (category == null) {
            return;
        }
        
        // 카테고리 코드 추출
        Object codeObj = category.get("displayItemCategoryCode");
        if (codeObj == null) {
            codeObj = category.get("displayCategoryCode");
        }
        if (codeObj == null) {
            return; // 코드가 없으면 스킵
        }
        
        String categoryCode = String.valueOf(codeObj);
        if ("null".equals(categoryCode) || "0".equals(categoryCode)) {
            // ROOT 카테고리는 스킵
            Object nameObj = category.get("name");
            if (nameObj != null && "ROOT".equals(String.valueOf(nameObj))) {
                // ROOT의 자식들만 처리
            } else {
                return;
            }
        }
        
        // 카테고리 이름 추출
        Object nameObj = category.get("name");
        if (nameObj == null) {
            return;
        }
        
        String categoryName = String.valueOf(nameObj);
        if ("null".equals(categoryName) || categoryName.trim().isEmpty()) {
            return;
        }
        
        // 현재 경로 구성
        String currentPath;
        if (parentPath == null || parentPath.isEmpty()) {
            currentPath = categoryName.trim();
        } else {
            currentPath = parentPath + " > " + categoryName.trim();
        }
        
        // 하위 카테고리 확인
        Object childObj = category.get("child");
        List<Map<String, Object>> children = null;
        if (childObj instanceof List) {
            children = (List<Map<String, Object>>) childObj;
        }
        
        boolean isLeaf = (children == null || children.isEmpty());
        
        // 리프 카테고리인 경우에만 매핑에 추가
        if (isLeaf && !"0".equals(categoryCode) && !"ROOT".equals(categoryName)) {
            String normalizedPath = currentPath.trim();
            categoryMappingCache.put(normalizedPath, categoryCode);
            
            // " > " 형식도 추가 (공백 차이 허용)
            String altPath = normalizedPath.replace(" > ", ">");
            if (!altPath.equals(normalizedPath)) {
                categoryMappingCache.put(altPath, categoryCode);
            }
            
            if (categoryMappingCache.size() <= 10) {
                log.debug("카테고리 매핑 추가: {} -> {}", normalizedPath, categoryCode);
            }
        }
        
        // 하위 카테고리 재귀 처리
        if (children != null && !children.isEmpty()) {
            for (Map<String, Object> child : children) {
                buildCategoryMapping(child, currentPath);
            }
        }
    }
    
    /**
     * 빈 매핑 초기화 (API 실패 시)
     */
    private void initializeEmptyMapping() {
        categoryMappingCache = new HashMap<>();
        log.warn("카테고리 매핑이 비어있습니다. 카테고리 코드를 직접 입력해야 합니다.");
    }
    
    /**
     * 한글 카테고리 경로를 쿠팡 API 카테고리 코드로 변환
     * 
     * @param categoryPath 한글 카테고리 경로 (예: "패션의류잡화 > 여성패션 > 여성의류 > 티셔츠")
     * @return 쿠팡 API 카테고리 코드 (예: "69184"), 매핑이 없으면 null
     */
    public String convertToCategoryCode(String categoryPath) {
        if (categoryPath == null || categoryPath.trim().isEmpty()) {
            return null;
        }
        
        // 매핑 테이블 초기화 (첫 호출 시에만)
        initializeCategoryMapping();
        
        String trimmedPath = categoryPath.trim();
        
        // 정확한 매칭 시도
        String categoryCode = categoryMappingCache.get(trimmedPath);
        if (categoryCode != null) {
            log.debug("카테고리 매핑 성공: {} -> {}", trimmedPath, categoryCode);
            return categoryCode;
        }
        
        // 구분자 형식 변환하여 매칭 시도
        // API는 ">" 형식, 엑셀은 " > " 형식 사용
        String[] variations = {
            trimmedPath,  // 원본
            trimmedPath.replaceAll("\\s*>\\s*", ">"),  // " > " -> ">"
            trimmedPath.replaceAll("\\s*>\\s*", " > "),  // ">" -> " > "
            trimmedPath.replace(">", " > "),  // ">" -> " > "
            trimmedPath.replace(" > ", ">")   // " > " -> ">"
        };
        
        for (String variation : variations) {
            categoryCode = categoryMappingCache.get(variation);
            if (categoryCode != null) {
                log.debug("카테고리 매핑 성공 (구분자 변환): {} -> {} (변환: {})", trimmedPath, categoryCode, variation);
                return categoryCode;
            }
        }
        
        // 대소문자 무시 매칭 시도
        for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmedPath)) {
                log.debug("카테고리 매핑 성공 (대소문자 무시): {} -> {}", trimmedPath, entry.getValue());
                return entry.getValue();
            }
        }
        
        // 유사한 경로 찾기 (마지막 부분만 일치하는 경우)
        String[] searchParts = trimmedPath.split("[>]");
        if (searchParts.length > 0) {
            String lastPart = searchParts[searchParts.length - 1].trim();
            
            // 마지막 부분이 정확히 일치하는 경로 찾기
            for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                String entryKey = entry.getKey();
                String[] entryParts = entryKey.split("[>]");
                
                if (entryParts.length > 0) {
                    String entryLastPart = entryParts[entryParts.length - 1].trim();
                    
                    if (entryLastPart.equals(lastPart)) {
                        log.info("카테고리 매핑 성공 (마지막 부분 일치): '{}' -> '{}' (코드: {})", 
                                trimmedPath, entryKey, entry.getValue());
                        return entry.getValue();
                    }
                }
            }
        }
        
        log.warn("카테고리 매핑 실패: '{}'에 해당하는 카테고리 코드를 찾을 수 없습니다.", trimmedPath);
        return null;
    }
    
    /**
     * 상품명을 기반으로 카테고리 추천
     * 
     * @param productName 상품명
     * @param productDescription 상품 상세설명 (선택)
     * @param brand 브랜드 (선택)
     * @return 추천된 카테고리 코드, 추천 실패 시 null
     */
    public String predictCategory(String productName, String productDescription, String brand) {
        if (productName == null || productName.trim().isEmpty()) {
            log.warn("카테고리 추천 실패: 상품명이 없습니다.");
            return null;
        }
        
        try {
            log.info("카테고리 추천 요청: 상품명={}", productName);
            
            Map<String, Object> response = coupangApiClient.predictCategory(
                    productName, 
                    productDescription, 
                    brand, 
                    null
            ).block();
            
            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                
                if (data != null && "SUCCESS".equals(data.get("autoCategorizationPredictionResultType"))) {
                    String predictedCategoryId = String.valueOf(data.get("predictedCategoryId"));
                    String predictedCategoryName = String.valueOf(data.get("predictedCategoryName"));
                    
                    log.info("카테고리 추천 성공: {} -> {} ({})", productName, predictedCategoryId, predictedCategoryName);
                    return predictedCategoryId;
                } else {
                    log.warn("카테고리 추천 실패: {}", data != null ? data.get("comment") : "알 수 없는 오류");
                }
            }
        } catch (Exception e) {
            log.error("카테고리 추천 중 오류 발생: {}", e.getMessage(), e);
        }
        
        return null;
    }
}
