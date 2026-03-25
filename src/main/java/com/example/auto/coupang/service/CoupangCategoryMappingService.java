package com.example.auto.coupang.service;

import com.example.auto.coupang.client.CoupangApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        
        // 디버깅: 검색 키워드가 포함된 카테고리 경로 찾기
        if (log.isDebugEnabled()) {
            String[] keywords = trimmedPath.split("[>]");
            if (keywords.length > 0) {
                String lastKeyword = keywords[keywords.length - 1].trim();
                log.debug("검색 키워드: '{}'", lastKeyword);
                int matchCount = 0;
                for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                    if (entry.getKey().contains(lastKeyword)) {
                        if (matchCount++ < 5) {
                            log.debug("  후보: {} -> {}", entry.getKey(), entry.getValue());
                        }
                    }
                }
                log.debug("총 {}개 카테고리가 '{}' 키워드를 포함함", matchCount, lastKeyword);
            }
        }
        
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
        
        // 경로 단계별 순차 매칭 (정확한 매핑을 위한 개선된 로직)
        String[] searchParts = trimmedPath.split("[>]");
        if (searchParts.length > 0) {
            // 각 부분 정리
            for (int i = 0; i < searchParts.length; i++) {
                searchParts[i] = searchParts[i].trim();
            }
            
            String lastPart = searchParts[searchParts.length - 1];
            String firstPart = searchParts[0];
            
            log.info("카테고리 매핑 시도: 전체 경로='{}', 경로 단계={}개", trimmedPath, searchParts.length);
            for (int i = 0; i < searchParts.length; i++) {
                log.debug("  검색 경로[{}]: '{}'", i, searchParts[i]);
            }
            
            // 상위 카테고리 매핑 테이블 (엑셀 경로 -> 쿠팡 API 경로)
            Map<String, String[]> topCategoryMapping = new HashMap<>();
            topCategoryMapping.put("의류", new String[]{"패션의류잡화", "패션의류"});
            topCategoryMapping.put("신발", new String[]{"패션의류잡화", "패션"});
            topCategoryMapping.put("식품", new String[]{"식품"});
            topCategoryMapping.put("가전", new String[]{"가전/디지털"});
            
            // 엑셀 경로에 제외 키워드가 있는지 확인
            String[] excludeKeywords = {"반려", "애완", "베이비", "영유아", "유아", "아동", "키즈", "주니어"};
            boolean searchPathHasExcludeKeyword = false;
            for (String keyword : excludeKeywords) {
                for (String part : searchParts) {
                    if (part.contains(keyword)) {
                        searchPathHasExcludeKeyword = true;
                        log.debug("엑셀 경로에 제외 키워드 발견: '{}' (키워드: '{}')", part, keyword);
                        break;
                    }
                }
                if (searchPathHasExcludeKeyword) break;
            }
            
            // 1단계: 마지막 부분이 일치하고, 경로의 각 단계가 순차적으로 매칭되는 카테고리 찾기
            List<Map.Entry<String, String>> candidateEntries = new ArrayList<>();
            
            for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                String entryKey = entry.getKey();
                String[] entryParts = entryKey.split("[>]");
                
                // 각 부분 정리
                for (int i = 0; i < entryParts.length; i++) {
                    entryParts[i] = entryParts[i].trim();
                }
                
                if (entryParts.length == 0) {
                    continue;
                }
                
                String entryFirstPart = entryParts[0];
                String entryLastPart = entryParts[entryParts.length - 1];
                
                // ROOT로 시작하는 경로는 ROOT를 제외하고 비교
                int entryStartIdx = "ROOT".equals(entryFirstPart) ? 1 : 0;
                int entryEndIdx = entryParts.length - 1;
                
                // 마지막 부분이 일치해야 함
                if (!entryLastPart.equals(lastPart)) {
                    continue;
                }
                
                // 경로 단계별 순차 매칭
                // 엑셀: "의류 > 상의 > 후드티" (3단계)
                // 쿠팡: "ROOT > 패션의류잡화 > 남성패션 > 남성의류 > 상의 > 후드티" (6단계)
                // → 엑셀의 각 단계가 쿠팡 경로의 어딘가에 순서대로 나타나야 함
                
                int searchIdx = 0; // 엑셀 경로 인덱스
                int entryIdx = entryStartIdx; // 쿠팡 경로 인덱스
                boolean allMatched = true;
                
                while (searchIdx < searchParts.length && entryIdx <= entryEndIdx) {
                    String searchPart = searchParts[searchIdx];
                    boolean matched = false;
                    
                    // 첫 번째 단계(상위 카테고리)인 경우 특별 처리
                    if (searchIdx == 0 && topCategoryMapping.containsKey(searchPart)) {
                        // 상위 카테고리 매핑 테이블에 있는 경우, 매핑된 카테고리가 포함된 경로만 매칭
                        String[] mappedCategories = topCategoryMapping.get(searchPart);
                        
                        for (int i = entryIdx; i <= entryEndIdx; i++) {
                            String entryPart = entryParts[i];
                            
                            // 엑셀 경로에 제외 키워드가 없을 때만 쿠팡 경로의 제외 키워드를 필터링
                            if (!searchPathHasExcludeKeyword) {
                                boolean hasExcludeKeyword = false;
                                for (String keyword : excludeKeywords) {
                                    if (entryPart.contains(keyword)) {
                                        hasExcludeKeyword = true;
                                        break;
                                    }
                                }
                                if (hasExcludeKeyword) {
                                    continue; // 제외 키워드가 포함된 경로는 스킵
                                }
                            }
                            
                            // 매핑된 카테고리와 매칭 확인
                            for (String mapped : mappedCategories) {
                                if (entryPart.contains(mapped) || mapped.contains(entryPart) || entryPart.equals(mapped)) {
                                    matched = true;
                                    entryIdx = i + 1;
                                    break;
                                }
                            }
                            if (matched) break;
                        }
                    } else {
                        // 중간/마지막 단계는 일반 매칭
                        // 정확히 일치하는 것을 우선 찾기
                        boolean exactMatch = false;
                        for (int i = entryIdx; i <= entryEndIdx; i++) {
                            String entryPart = entryParts[i];
                            if (entryPart.equals(searchPart)) {
                                matched = true;
                                exactMatch = true;
                                entryIdx = i + 1;
                                break;
                            }
                        }
                        
                        // 정확히 일치하지 않으면 포함 관계 확인
                        if (!exactMatch) {
                            for (int i = entryIdx; i <= entryEndIdx; i++) {
                                String entryPart = entryParts[i];
                                // 포함 관계 (예: "상의"와 "남성의류 > 상의")
                                // 단, 마지막 단계가 아니고, 포함 관계가 너무 넓지 않은 경우만
                                if (entryPart.contains(searchPart) || searchPart.contains(entryPart)) {
                                    // 엑셀 경로에 제외 키워드가 없을 때만 쿠팡 경로의 제외 키워드를 필터링
                                    if (!searchPathHasExcludeKeyword) {
                                        boolean hasExcludeKeyword = false;
                                        for (String keyword : excludeKeywords) {
                                            if (entryPart.contains(keyword)) {
                                                hasExcludeKeyword = true;
                                                break;
                                            }
                                        }
                                        if (hasExcludeKeyword) {
                                            continue; // 제외 키워드가 포함된 경로는 스킵
                                        }
                                    }
                                    matched = true;
                                    entryIdx = i + 1;
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (!matched) {
                        allMatched = false;
                        break;
                    }
                    
                    searchIdx++;
                }
                
                // 모든 단계가 순차적으로 매칭되었고, 마지막 부분도 일치하는 경우
                if (allMatched && searchIdx == searchParts.length) {
                    // 엑셀 경로에 제외 키워드가 없을 때만 쿠팡 경로의 제외 키워드를 필터링
                    // 엑셀 경로에 제외 키워드가 있으면 해당 카테고리를 판매하는 것이므로 매칭 허용
                    boolean shouldExclude = false;
                    if (!searchPathHasExcludeKeyword) {
                        // 엑셀 경로에 제외 키워드가 없으면, 쿠팡 경로에 제외 키워드가 있으면 제외
                        for (String keyword : excludeKeywords) {
                            if (entryKey.contains(keyword)) {
                                shouldExclude = true;
                                log.debug("순차 매칭 후보 제외 (엑셀 경로에 제외 키워드 없음, 쿠팡 경로에 제외 키워드 포함): '{}'", entryKey);
                                break;
                            }
                        }
                    }
                    // 엑셀 경로에 제외 키워드가 있으면 쿠팡 경로의 제외 키워드와 관계없이 매칭 허용
                    
                    if (!shouldExclude) {
                        log.debug("순차 매칭 후보 발견: '{}' -> '{}' (코드: {})", trimmedPath, entryKey, entry.getValue());
                        candidateEntries.add(entry);
                    }
                }
            }
            
            log.debug("순차 매칭 후보: {}개", candidateEntries.size());
            
            // 후보 중에서 가장 적합한 것 선택 (경로 길이와 일치도 고려)
            if (!candidateEntries.isEmpty()) {
                Map.Entry<String, String> bestMatch = null;
                int bestScore = 0;
                
                for (Map.Entry<String, String> entry : candidateEntries) {
                    String entryKey = entry.getKey();
                    String[] entryParts = entryKey.split("[>]");
                    int score = 0;
                    
                    // 경로 길이가 비슷할수록 높은 점수
                    int lengthDiff = Math.abs(entryParts.length - searchParts.length);
                    if (lengthDiff == 0) {
                        score += 20;
                    } else if (lengthDiff <= 2) {
                        score += 10;
                    } else if (lengthDiff <= 3) {
                        score += 5;
                    }
                    
                    // 중간 경로의 정확한 일치도
                    int matchedParts = 0;
                    int entryStartIdx = entryParts[0].trim().equals("ROOT") ? 1 : 0;
                    int searchIdx = 0;
                    int entryIdx = entryStartIdx;
                    
                    while (searchIdx < searchParts.length && entryIdx < entryParts.length) {
                        String searchPart = searchParts[searchIdx].trim();
                        String entryPart = entryParts[entryIdx].trim();
                        
                        if (entryPart.equals(searchPart)) {
                            score += 10;
                            matchedParts++;
                            searchIdx++;
                            entryIdx++;
                        } else if (entryPart.contains(searchPart) || searchPart.contains(entryPart)) {
                            score += 5;
                            matchedParts++;
                            searchIdx++;
                            entryIdx++;
                        } else {
                            entryIdx++;
                        }
                    }
                    
                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = entry;
                    }
                }
                
                if (bestMatch != null) {
                    log.info("카테고리 매핑 성공 (순차 경로 매칭): '{}' -> '{}' (코드: {}, 점수: {})", 
                            trimmedPath, bestMatch.getKey(), bestMatch.getValue(), bestScore);
                    return bestMatch.getValue();
                }
            }
            
            log.debug("순차 경로 매칭 실패, 추천 API 사용 권장: '{}'", trimmedPath);
        }
        
        log.warn("카테고리 매핑 실패: '{}'에 해당하는 카테고리 코드를 찾을 수 없습니다. 추천 API를 사용합니다.", trimmedPath);
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
