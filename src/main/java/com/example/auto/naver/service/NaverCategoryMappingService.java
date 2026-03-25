package com.example.auto.naver.service;

import com.example.auto.naver.constants.NaverApiConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 네이버 카테고리 경로를 네이버 API 카테고리 ID로 변환하는 서비스
 * 
 * 네이버 스마트스토어 카테고리 조회 API를 사용하여 동적으로 카테고리 매핑을 생성합니다.
 * 첫 호출 시 API를 통해 전체 카테고리를 조회하고, 이후에는 캐시된 매핑을 사용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverCategoryMappingService {
    
    private final NaverProductService productService;
    
    /**
     * 카테고리 경로 -> 카테고리 ID 매핑 캐시
     * 형식: "대분류 > 중분류 > 소분류" -> "카테고리ID"
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
            log.info("카테고리 매핑 초기화 시작: 네이버 API에서 카테고리 조회...");
            
            // 현재 스토어의 카테고리 조회 (전체 트리 구조 필요)
            List<Map<String, Object>> categories = productService.getCategories(false);
            
            if (categories != null && !categories.isEmpty()) {
                categoryMappingCache = new HashMap<>();
                
                // 응답 구조 디버깅
                log.debug("카테고리 응답 첫 번째 항목: {}", categories.get(0));
                log.debug("카테고리 응답 첫 번째 항목의 키들: {}", categories.get(0).keySet());
                
                buildCategoryMapping(categories, null, "");
                
                if (categoryMappingCache.isEmpty()) {
                    log.warn("카테고리 매핑이 생성되지 않았습니다. 응답 구조를 확인하세요.");
                    log.warn("첫 번째 카테고리 항목: {}", categories.get(0));
                } else {
                    log.info("카테고리 매핑 초기화 완료: {}개 카테고리 매핑 생성", categoryMappingCache.size());
                    
                    // 전체 카테고리 매핑을 출력 (복사하기 쉽게)
                    // 로그 길이 제한을 피하기 위해 여러 줄로 나누어 출력
                    StringBuilder allCategories = new StringBuilder();
                    allCategories.append("전체 카테고리 매핑 (").append(categoryMappingCache.size()).append("개): ");
                    int count = 0;
                    int lineCount = 0;
                    final int MAX_LINE_LENGTH = NaverApiConstants.MAX_LOG_LINE_LENGTH; // 한 줄당 최대 길이
                    
                    for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                        if (count > 0) {
                            allCategories.append(" | ");
                        }
                        allCategories.append(entry.getKey()).append("=").append(entry.getValue());
                        count++;
                        
                        // 한 줄이 너무 길어지면 출력하고 새 줄 시작
                        if (allCategories.length() > MAX_LINE_LENGTH) {
                            log.info("카테고리 매핑 ({}): {}", lineCount + 1, allCategories.toString());
                            allCategories = new StringBuilder();
                            lineCount++;
                        }
                    }
                    
                    // 마지막 남은 부분 출력
                    if (allCategories.length() > 0) {
                        log.info("카테고리 매핑 ({}): {}", lineCount + 1, allCategories.toString());
                    }
                    
                    log.info("전체 카테고리 매핑 출력 완료: 총 {}개, {}줄", categoryMappingCache.size(), lineCount + 1);
                }
            } else {
                log.warn("카테고리 조회 결과가 비어있습니다. 하드코딩된 매핑을 사용합니다.");
                initializeHardcodedMapping();
            }
        } catch (Exception e) {
            log.error("카테고리 매핑 초기화 실패: {}", e.getMessage(), e);
            log.warn("하드코딩된 매핑을 사용합니다.");
            initializeHardcodedMapping();
        }
    }
    
    /**
     * 카테고리 트리를 순회하며 매핑 테이블 생성
     * 
     * @param categories 카테고리 리스트
     * @param parentPath 부모 카테고리 경로
     * @param parentName 부모 카테고리 이름
     */
    @SuppressWarnings("unchecked")
    private void buildCategoryMapping(List<Map<String, Object>> categories, String parentPath, String parentName) {
        if (categories == null || categories.isEmpty()) {
            return;
        }
        
        // 첫 번째 카테고리의 키를 확인하여 응답 구조 파악
        if (categoryMappingCache.isEmpty() && !categories.isEmpty()) {
            Map<String, Object> firstCategory = categories.get(0);
            log.info("카테고리 응답 구조 확인: 첫 번째 카테고리 키들 = {}", firstCategory.keySet());
            log.info("카테고리 응답 샘플 (첫 번째): {}", firstCategory);
            
            // 처음 3개 카테고리 샘플 출력
            for (int i = 0; i < Math.min(3, categories.size()); i++) {
                log.info("카테고리 샘플 {}: {}", i + 1, categories.get(i));
            }
            
            // 디버깅: 특정 키워드가 포함된 카테고리 찾기 (재귀적으로 모든 카테고리 검색)
            String[] debugKeywords = {"후드티", "반팔티", "스니커즈", "로우탑", "상의", "의류"};
            log.info("=== 디버깅: 특정 키워드가 포함된 카테고리 검색 (전체 트리) ===");
            for (String keyword : debugKeywords) {
                List<String> foundCategories = findCategoriesByKeyword(categories, keyword, new ArrayList<>());
                if (!foundCategories.isEmpty()) {
                    log.info("키워드 '{}' 관련 카테고리 (최대 20개):", keyword);
                    for (int i = 0; i < Math.min(20, foundCategories.size()); i++) {
                        log.info("  {}: {}", i + 1, foundCategories.get(i));
                    }
                } else {
                    log.info("키워드 '{}' 관련 카테고리 없음", keyword);
                }
            }
            log.info("=== 디버깅: 키워드 검색 완료 ===");
        }
        
        for (Map<String, Object> category : categories) {
            // 카테고리 ID 추출
            Object idObj = category.get("id");
            if (idObj == null) {
                idObj = category.get("categoryId");
            }
            if (idObj == null) {
                idObj = category.get("leafCategoryId");
            }
            if (idObj == null) {
                log.warn("카테고리 ID 필드 누락: category={}", category);
                continue;
            }
            String categoryId = String.valueOf(idObj);
            if ("null".equals(categoryId)) {
                continue;
            }
            
            // wholeCategoryName 사용 (전체 경로)
            // API 응답 구조: wholeCategoryName="패션의류>여성의류>니트/스웨터"
            Object wholeCategoryNameObj = category.get("wholeCategoryName");
            String wholeCategoryPath = null;
            if (wholeCategoryNameObj != null) {
                wholeCategoryPath = String.valueOf(wholeCategoryNameObj);
                // ">"를 " > "로 변환 (엑셀 형식과 일치시키기)
                wholeCategoryPath = wholeCategoryPath.replace(">", " > ");
            }
            
            // last 필드 확인 (리프 카테고리 여부)
            Object lastObj = category.get("last");
            boolean isLeaf = false;
            if (lastObj != null) {
                isLeaf = Boolean.TRUE.equals(lastObj) || "true".equalsIgnoreCase(String.valueOf(lastObj));
            } else {
                // last 필드가 없으면 children으로 판단
                List<Map<String, Object>> children = (List<Map<String, Object>>) category.get("children");
                isLeaf = (children == null || children.isEmpty());
            }
            
            // 리프 카테고리인 경우에만 매핑에 추가
            if (isLeaf) {
                if (wholeCategoryPath != null && !"null".equals(wholeCategoryPath) && !wholeCategoryPath.trim().isEmpty()) {
                    // wholeCategoryName 사용
                    String normalizedPath = wholeCategoryPath.trim();
                    categoryMappingCache.put(normalizedPath, categoryId);
                    
                    // " > " 형식도 추가 (공백 차이 허용)
                    String altPath = normalizedPath.replace(" > ", ">");
                    if (!altPath.equals(normalizedPath)) {
                        categoryMappingCache.put(altPath, categoryId);
                    }
                    
                    if (categoryMappingCache.size() <= 10) {
                        log.info("카테고리 매핑 추가: {} -> {}", normalizedPath, categoryId);
                    }
                } else {
                    // wholeCategoryName이 없으면 name으로 경로 구성 (폴백)
                    Object nameObj = category.get("name");
                    if (nameObj != null) {
                        String categoryName = String.valueOf(nameObj);
                        String currentPath = parentPath == null || parentPath.isEmpty() 
                                ? categoryName.trim() 
                                : (parentPath + " > " + categoryName.trim());
                        categoryMappingCache.put(currentPath, categoryId);
                        if (categoryMappingCache.size() <= 10) {
                            log.warn("wholeCategoryName 없음, name으로 경로 구성: {} -> {}", currentPath, categoryId);
                        }
                    }
                }
            } else {
                // 중간 카테고리: 자식 카테고리 재귀 처리
                List<Map<String, Object>> children = (List<Map<String, Object>>) category.get("children");
                if (children != null && !children.isEmpty()) {
                    // 재귀 호출 시 parentPath 업데이트
                    String nextParentPath = wholeCategoryPath != null && !"null".equals(wholeCategoryPath) 
                            ? wholeCategoryPath.trim() 
                            : (parentPath == null || parentPath.isEmpty() 
                                    ? (category.get("name") != null ? String.valueOf(category.get("name")).trim() : "") 
                                    : (parentPath + " > " + (category.get("name") != null ? String.valueOf(category.get("name")).trim() : "")));
                    buildCategoryMapping(children, nextParentPath, null);
                }
            }
        }
    }
    
    /**
     * 하드코딩된 매핑 테이블 초기화 (API 실패 시 폴백)
     * 기존 하드코딩된 매핑을 유지하여 호환성 보장
     */
    private void initializeHardcodedMapping() {
        categoryMappingCache = new HashMap<>();
        
        // 패션의류
        categoryMappingCache.put("패션의류 > 상의 > 티셔츠", "50000805");
        categoryMappingCache.put("패션의류 > 하의 > 청바지", "50000806");
        categoryMappingCache.put("패션의류 > 하의 > 슬랙스", "50000807");
        
        // 패션잡화
        categoryMappingCache.put("패션잡화 > 가방 > 백팩", "50000808");
        categoryMappingCache.put("패션잡화 > 가방 > 토트백", "50000809");
        
        // 식품
        categoryMappingCache.put("식품 > 과자/간식 > 과자", "50000810");
        categoryMappingCache.put("식품 > 과자/간식 > 초콜릿", "50000811");
        
        // 디지털/가전
        categoryMappingCache.put("디지털/가전 > 모니터", "50000812");
        categoryMappingCache.put("디지털/가전 > 노트북", "50000813");
        
        // 생활/주방
        categoryMappingCache.put("생활/주방 > 주방용품 > 프라이팬", "50000814");
        
        // 반려동물
        categoryMappingCache.put("반려동물 > 강아지용품 > 사료", "50000815");
        
        log.info("하드코딩된 카테고리 매핑 초기화 완료: {}개", categoryMappingCache.size());
    }
    
    /**
     * 한글 카테고리 경로를 네이버 API 카테고리 ID로 변환
     * 
     * @param categoryPath 한글 카테고리 경로 (예: "패션의류 > 상의 > 티셔츠")
     * @return 네이버 API 카테고리 ID (예: "50000805"), 매핑이 없으면 null
     */
    public String convertToCategoryId(String categoryPath) {
        if (categoryPath == null || categoryPath.trim().isEmpty()) {
            return null;
        }
        
        // 매핑 테이블 초기화 (첫 호출 시에만)
        initializeCategoryMapping();
        
        String trimmedPath = categoryPath.trim();
        
        // 정확한 매칭 시도
        String categoryId = categoryMappingCache.get(trimmedPath);
        if (categoryId != null) {
            log.debug("카테고리 매핑 성공: {} -> {}", trimmedPath, categoryId);
            return categoryId;
        }
        
        // 구분자 형식 변환하여 매칭 시도
        // API는 ">" 형식, 엑셀은 " > " 형식 사용
        // 양쪽 형식 모두 시도
        String[] variations = {
            trimmedPath,  // 원본
            trimmedPath.replaceAll("\\s*>\\s*", ">"),  // " > " -> ">"
            trimmedPath.replaceAll("\\s*>\\s*", " > "),  // ">" -> " > "
            trimmedPath.replace(">", " > "),  // ">" -> " > "
            trimmedPath.replace(" > ", ">")   // " > " -> ">"
        };
        
        for (String variation : variations) {
            categoryId = categoryMappingCache.get(variation);
            if (categoryId != null) {
                log.debug("카테고리 매핑 성공 (구분자 변환): {} -> {} (변환: {})", trimmedPath, categoryId, variation);
                return categoryId;
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
            
            log.info("카테고리 매핑 시도: 전체 경로='{}', 경로 단계={}개", trimmedPath, searchParts.length);
            for (int i = 0; i < searchParts.length; i++) {
                log.info("  검색 경로[{}]: '{}'", i, searchParts[i]);
            }
            
            // 디버깅: 검색어와 관련된 실제 네이버 카테고리 찾기
            log.info("=== 디버깅: 검색어 '{}'와 관련된 네이버 카테고리 검색 시작 ===", trimmedPath);
            List<String> debugRelatedCategories = new ArrayList<>();
            for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                String entryKey = entry.getKey();
                // 검색어의 각 부분이 포함된 카테고리 찾기
                boolean isRelated = false;
                for (String searchPart : searchParts) {
                    if (entryKey.contains(searchPart) || searchPart.contains(entryKey)) {
                        isRelated = true;
                        break;
                    }
                }
                if (isRelated) {
                    debugRelatedCategories.add(entryKey + " -> " + entry.getValue());
                    if (debugRelatedCategories.size() >= 30) break;
                }
            }
            log.info("관련 카테고리 발견: {}개 (최대 30개)", debugRelatedCategories.size());
            for (int i = 0; i < Math.min(30, debugRelatedCategories.size()); i++) {
                log.info("  관련 카테고리 {}: {}", i + 1, debugRelatedCategories.get(i));
            }
            log.info("=== 디버깅: 관련 카테고리 검색 완료 ===");
            
            // 상위 카테고리 매핑 테이블 (엑셀 경로 -> 네이버 API 경로)
            // 스포츠 키워드가 있으면 스포츠/레저도 매핑 가능, 없으면 일반 의류만 매핑
            Map<String, String[]> topCategoryMapping = new HashMap<>();
            // "의류"는 스포츠 키워드가 있으면 스포츠/레저도 매핑, 없으면 패션의류만 매핑
            // (동적으로 결정되므로 여기서는 기본값만 설정, 실제 로직에서 조건부로 처리)
            topCategoryMapping.put("의류", new String[]{"패션의류", "패션의류잡화"});
            // "신발"은 패션잡화에 포함됨
            topCategoryMapping.put("신발", new String[]{"패션잡화", "패션의류잡화"});
            topCategoryMapping.put("식품", new String[]{"식품"});
            topCategoryMapping.put("가전", new String[]{"가전/디지털", "디지털/가전"});
            topCategoryMapping.put("패션의류", new String[]{"패션의류", "패션의류잡화"});
            topCategoryMapping.put("패션잡화", new String[]{"패션의류", "패션의류잡화", "패션잡화"});
            // "상의"는 스포츠 키워드가 있으면 스포츠/레저도 매핑, 없으면 패션의류만 매핑
            topCategoryMapping.put("상의", new String[]{"패션의류"});
            // "하의"도 마찬가지
            topCategoryMapping.put("하의", new String[]{"패션의류"});
            // 스포츠/레저는 명시적으로 입력된 경우만 매칭
            topCategoryMapping.put("스포츠", new String[]{"스포츠/레저", "스포츠레저"});
            topCategoryMapping.put("레저", new String[]{"스포츠/레저", "스포츠레저"});
            
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
            
            // 엑셀 경로에 스포츠 관련 키워드가 있는지 확인
            // 스포츠 키워드가 있으면 스포츠/레저 카테고리로도 매핑 가능, 없으면 일반 의류만 매핑
            String[] sportKeywords = {"스포츠", "스포츠/레저", "스포츠레저", "스키", "보드", "보드복", "스키복", 
                                     "등산", "등산의류", "자전거", "수영", "요가", "필라테스", "레저"};
            boolean searchPathHasSportKeyword = false;
            for (String keyword : sportKeywords) {
                for (String part : searchParts) {
                    if (part.contains(keyword)) {
                        searchPathHasSportKeyword = true;
                        log.debug("엑셀 경로에 스포츠 키워드 발견: '{}' (키워드: '{}')", part, keyword);
                        break;
                    }
                }
                if (searchPathHasSportKeyword) break;
            }
            
            log.info("엑셀 경로 스포츠 키워드 검사 결과: 경로='{}', 스포츠 키워드 포함={}", trimmedPath, searchPathHasSportKeyword);
            
            // 1단계: 마지막 부분이 일치하거나 포함 관계가 있고, 경로의 각 단계가 순차적으로 매칭되는 카테고리 찾기
            List<Map.Entry<String, String>> candidateEntries = new ArrayList<>();
            
            // 디버깅: 마지막 부분과 관련된 카테고리 찾기
            List<String> relatedCategories = new ArrayList<>();
            // "로우탑" 같은 특수 케이스: "스니커즈" 카테고리에서 찾을 수 있음
            String[] lastPartVariations = {lastPart};
            if (lastPart.equals("로우탑")) {
                lastPartVariations = new String[]{"로우탑", "스니커즈", "로우"};
            } else if (lastPart.contains("스니커즈")) {
                lastPartVariations = new String[]{lastPart, "스니커즈"};
            } else if (lastPart.equals("후드티")) {
                lastPartVariations = new String[]{"후드티", "후드티셔츠", "후드", "후디"};
            } else if (lastPart.equals("반팔티")) {
                lastPartVariations = new String[]{"반팔티", "반팔티셔츠", "반팔", "티셔츠"};
            }
            
            for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                String entryKey = entry.getKey();
                String[] entryParts = entryKey.split("[>]");
                if (entryParts.length > 0) {
                    String entryLastPart = entryParts[entryParts.length - 1].trim();
                    // 여러 변형으로 매칭 시도
                    boolean matches = false;
                    for (String variation : lastPartVariations) {
                        if (entryLastPart.contains(variation) || variation.contains(entryLastPart)) {
                            matches = true;
                            break;
                        }
                    }
                    if (matches) {
                        relatedCategories.add(entryKey);
                        if (relatedCategories.size() >= 20) break;
                    }
                }
            }
            if (!relatedCategories.isEmpty()) {
                log.debug("마지막 부분 '{}'와 관련된 카테고리 (최대 20개, 변형: {}): {}", 
                        lastPart, java.util.Arrays.toString(lastPartVariations), relatedCategories);
            }
            
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
                
                // 첫 번째 단계 필터링: 스포츠 키워드가 없을 때만 스포츠/레저 제외
                if (searchParts.length > 0) {
                    String firstSearchPart = searchParts[0].trim();
                    String entryTopCategory = entryParts[0].trim();
                    
                    // 일반 의류("의류", "상의", "하의")인 경우
                    if ((firstSearchPart.equals("의류") || firstSearchPart.equals("상의") || firstSearchPart.equals("하의"))) {
                        // 스포츠 키워드가 없으면 스포츠/레저로 시작하는 경로 제외
                        if (!searchPathHasSportKeyword) {
                            if (entryTopCategory.contains("스포츠") || entryTopCategory.contains("레저")) {
                                log.debug("후보 제외: 일반 의류(스포츠 키워드 없음)는 스포츠/레저 카테고리 제외 - '{}' (최상위: '{}')", entryKey, entryTopCategory);
                                continue;
                            }
                            // 일반 의류는 패션의류로 시작하는 경로만 허용
                            if (!entryTopCategory.contains("패션의류")) {
                                log.debug("후보 제외: 일반 의류는 패션의류로 시작해야 함 - '{}' (최상위: '{}')", entryKey, entryTopCategory);
                                continue;
                            }
                        } else {
                            // 스포츠 키워드가 있으면 스포츠/레저도 허용
                            log.debug("후보 허용: 스포츠 키워드 포함으로 스포츠/레저 카테고리도 허용 - '{}' (최상위: '{}')", entryKey, entryTopCategory);
                        }
                    }
                    
                    // 신발은 패션잡화 우선, 스포츠 신발은 낮은 우선순위 (나중에 점수로 처리)
                }
                
                String entryLastPart = entryParts[entryParts.length - 1];
                
                // 마지막 부분이 일치하거나 포함 관계가 있어야 함 (유연한 매칭)
                // 예: "후드티" <-> "후드티셔츠", "반팔티" <-> "반팔티셔츠"
                // "로우탑" 같은 경우는 "스니커즈" 카테고리에서 찾을 수 있으므로 더 유연하게 매칭
                boolean lastPartMatches = false;
                
                // 기본 매칭
                if (entryLastPart.equals(lastPart) 
                    || entryLastPart.contains(lastPart) 
                    || lastPart.contains(entryLastPart)) {
                    lastPartMatches = true;
                }
                
                // 특수 케이스: "로우탑"은 "스니커즈" 카테고리에서 찾을 수 있음
                if (!lastPartMatches && lastPart.equals("로우탑")) {
                    // "스니커즈"를 포함한 카테고리도 매칭
                    if (entryLastPart.contains("스니커즈")) {
                        lastPartMatches = true;
                        log.debug("특수 케이스 매칭: '로우탑' -> '{}' (스니커즈 포함)", entryLastPart);
                    }
                }
                
                // "스니커즈"가 마지막 부분인 경우도 처리
                if (!lastPartMatches && lastPart.contains("스니커즈") && entryLastPart.contains("스니커즈")) {
                    lastPartMatches = true;
                }
                
                // 특수 케이스: "후드티"는 "후드티셔츠", "후드", "후디" 등으로 매칭 가능
                if (!lastPartMatches && lastPart.equals("후드티")) {
                    if (entryLastPart.contains("후드") || entryLastPart.contains("후디") || entryLastPart.contains("후드티")) {
                        lastPartMatches = true;
                        log.debug("특수 케이스 매칭: '후드티' -> '{}' (후드 관련)", entryLastPart);
                    }
                }
                
                // 특수 케이스: "반팔티"는 "반팔티셔츠", "반팔", "티셔츠" 등으로 매칭 가능
                if (!lastPartMatches && lastPart.equals("반팔티")) {
                    if (entryLastPart.contains("반팔") || entryLastPart.contains("티셔츠") || entryLastPart.contains("반팔티")) {
                        lastPartMatches = true;
                        log.debug("특수 케이스 매칭: '반팔티' -> '{}' (반팔/티셔츠 관련)", entryLastPart);
                    }
                }
                
                if (!lastPartMatches) {
                    continue;
                }
                
                // 경로 단계별 순차 매칭 (유연한 매칭: 네이버 경로의 중간 단계 건너뛰기 허용)
                // 엑셀: "의류 > 상의 > 후드티" (3단계)
                // 네이버: "패션의류 > 여성의류 > 상의 > 후드티" (4단계)
                // → 엑셀의 각 단계가 네이버 경로의 어딘가에 순서대로 나타나야 함
                // → 네이버 경로의 중간 단계(예: "여성의류", "남성의류")는 건너뛸 수 있음
                
                // 건너뛸 수 있는 중간 단계 키워드 (성별, 연령대 등)
                String[] skippableKeywords = {"여성의류", "남성의류", "여성", "남성", "공용", "유니섹스", 
                                              "아동", "키즈", "주니어", "베이비", "영유아", "유아"};
                
                // 건너뛸 수 있는 일반 카테고리 키워드 (네이버에서 세부 분류로 대체되는 경우)
                // 예: "상의" -> "여성의류 > 티셔츠" (상의 단계가 없음)
                String[] skippableGeneralCategories = {"상의", "하의", "아우터", "신발"};
                
                int searchIdx = 0; // 엑셀 경로 인덱스
                int entryIdx = 0; // 네이버 경로 인덱스
                boolean allMatched = true;
                
                // 각 검색 단계를 순차적으로 매칭
                log.info("=== 순차 매칭 시작: 검색 경로='{}', 네이버 경로='{}' ===", trimmedPath, entryKey);
                for (searchIdx = 0; searchIdx < searchParts.length; searchIdx++) {
                    String searchPart = searchParts[searchIdx];
                    boolean matched = false;
                    
                    log.info("  [단계 {}] 검색어: '{}', 현재 entryIdx: {}, 네이버 경로: {}", 
                            searchIdx, searchPart, entryIdx, java.util.Arrays.toString(entryParts));
                    
                    // 첫 번째 단계(상위 카테고리)인 경우 특별 처리
                    if (searchIdx == 0 && topCategoryMapping.containsKey(searchPart)) {
                        // 상위 카테고리 매핑 테이블에 있는 경우, 매핑된 카테고리가 포함된 경로만 매칭
                        String[] baseMappedCategories = topCategoryMapping.get(searchPart);
                        
                        // 스포츠 키워드가 있으면 스포츠/레저도 매핑 후보에 추가
                        List<String> mappedCategoriesList = new ArrayList<>(java.util.Arrays.asList(baseMappedCategories));
                        if (searchPathHasSportKeyword && 
                            (searchPart.equals("의류") || searchPart.equals("상의") || searchPart.equals("하의"))) {
                            mappedCategoriesList.add("스포츠/레저");
                            mappedCategoriesList.add("스포츠레저");
                            log.info("  [단계 {}] 스포츠 키워드 발견으로 스포츠/레저 매핑 후보 추가", searchIdx);
                        }
                        String[] mappedCategories = mappedCategoriesList.toArray(new String[0]);
                        
                        log.info("  [단계 {}] 첫 번째 단계 매칭 시도: '{}' -> 매핑 후보: {}", 
                                searchIdx, searchPart, java.util.Arrays.toString(mappedCategories));
                        
                        // 네이버 경로의 첫 번째 부분부터 확인 (최상위 카테고리는 보통 첫 번째에 위치)
                        // 일반 의류는 스포츠 키워드가 있으면 스포츠/레저도 확인, 없으면 패션의류만 확인
                        int maxCheckLevel = searchPart.equals("의류") || searchPart.equals("상의") || searchPart.equals("하의") 
                            ? 1  // 일반 의류는 첫 번째 단계만 확인
                            : Math.min(3, entryParts.length);  // 기타는 최상위 3단계까지 확인
                        
                        for (int i = 0; i < maxCheckLevel; i++) {
                            String entryPart = entryParts[i];
                            log.debug("    entryParts[{}]: '{}' 확인 중...", i, entryPart);
                            
                            // 엑셀 경로에 제외 키워드가 없을 때만 네이버 경로의 제외 키워드를 필터링
                            if (!searchPathHasExcludeKeyword) {
                                boolean hasExcludeKeyword = false;
                                for (String keyword : excludeKeywords) {
                                    if (entryPart.contains(keyword)) {
                                        hasExcludeKeyword = true;
                                        log.debug("      제외 키워드 발견: '{}' (키워드: '{}')", entryPart, keyword);
                                        break;
                                    }
                                }
                                if (hasExcludeKeyword) {
                                    continue; // 제외 키워드가 포함된 경로는 스킵
                                }
                            }
                            
                            // 매핑된 카테고리와 매칭 확인 (더 유연한 매칭)
                            for (String mapped : mappedCategories) {
                                // 정확 일치 또는 포함 관계 확인
                                boolean isMatch = entryPart.equals(mapped) 
                                    || entryPart.contains(mapped) 
                                    || mapped.contains(entryPart)
                                    || entryPart.replace("/", "").equals(mapped.replace("/", "")); // "/" 제거 후 비교
                                
                                // 추가: "의류" -> "패션의류" 매핑을 위한 특수 처리
                                // "의류"가 검색어이고, 네이버 경로에 "패션의류" 또는 "패션의류잡화"가 포함되어 있으면 매칭
                                if (!isMatch && searchPart.equals("의류")) {
                                    if (entryPart.contains("패션의류") || entryPart.contains("패션의류잡화")) {
                                        isMatch = true;
                                        log.debug("    특수 매칭: '의류' -> '{}' (패션의류 포함)", entryPart);
                                    }
                                }
                                
                                // 추가: "상의" -> "패션의류" 매핑을 위한 특수 처리
                                if (!isMatch && searchPart.equals("상의")) {
                                    if (entryPart.contains("패션의류") || entryPart.contains("패션의류잡화")) {
                                        isMatch = true;
                                        log.debug("    특수 매칭: '상의' -> '{}' (패션의류 포함)", entryPart);
                                    }
                                }
                                
                                // 추가: "하의" -> "패션의류" 매핑을 위한 특수 처리
                                if (!isMatch && searchPart.equals("하의")) {
                                    if (entryPart.contains("패션의류") || entryPart.contains("패션의류잡화")) {
                                        isMatch = true;
                                        log.debug("    특수 매칭: '하의' -> '{}' (패션의류 포함)", entryPart);
                                    }
                                }
                                
                                if (isMatch) {
                                    log.info("    [단계 {}] 첫 번째 단계 매칭 성공: '{}' <-> '{}' (매핑: '{}')", 
                                            searchIdx, searchPart, entryPart, mapped);
                                    matched = true;
                                    entryIdx = i + 1; // 매칭된 다음 위치로 이동
                                    log.info("    entryIdx를 {}로 업데이트", entryIdx);
                                    // 건너뛸 수 있는 중간 단계들을 건너뛰기
                                    while (entryIdx < entryParts.length) {
                                        String nextPart = entryParts[entryIdx];
                                        boolean isSkippable = false;
                                        for (String skippable : skippableKeywords) {
                                            if (nextPart.contains(skippable)) {
                                                isSkippable = true;
                                                break;
                                            }
                                        }
                                        if (!isSkippable) {
                                            log.debug("      건너뛸 수 없는 단계 도달: '{}'", nextPart);
                                            break; // 건너뛸 수 없는 단계에 도달
                                        }
                                        log.debug("      건너뛰기: '{}'", nextPart);
                                        entryIdx++; // 건너뛰기
                                    }
                                    log.info("    건너뛰기 완료, 최종 entryIdx: {}", entryIdx);
                                    break;
                                }
                            }
                            if (matched) break;
                        }
                        if (!matched) {
                            log.warn("  [단계 {}] 첫 번째 단계 매칭 실패: '{}'", searchIdx, searchPart);
                            log.warn("    entryIdx: {}, entryParts: {}", entryIdx, java.util.Arrays.toString(entryParts));
                            log.warn("    매핑 후보: {}", java.util.Arrays.toString(mappedCategories));
                            log.warn("    maxCheckLevel: {}", maxCheckLevel);
                            // 디버깅: 실제 네이버 경로의 최상위 카테고리들 출력
                            log.warn("    네이버 경로 최상위 카테고리 (처음 {}개):", Math.min(maxCheckLevel, entryParts.length));
                            for (int i = 0; i < Math.min(maxCheckLevel, entryParts.length); i++) {
                                log.warn("      [{}]: '{}'", i, entryParts[i]);
                            }
                            // 매핑 후보와 실제 경로 비교
                            log.warn("    매핑 후보와 실제 경로 비교:");
                            for (String mapped : mappedCategories) {
                                boolean found = false;
                                for (int i = 0; i < Math.min(maxCheckLevel, entryParts.length); i++) {
                                    if (entryParts[i].contains(mapped) || mapped.contains(entryParts[i])) {
                                        log.warn("      매핑 후보 '{}' <-> 실제 경로 '{}' (일치)", mapped, entryParts[i]);
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    log.warn("      매핑 후보 '{}' <-> 실제 경로 (불일치)", mapped);
                                }
                            }
                            // 추가 디버깅: 왜 매칭이 실패했는지 분석
                            if (searchPart.equals("의류") || searchPart.equals("상의") || searchPart.equals("하의")) {
                                log.warn("    === 일반 의류 매칭 실패 분석 ===");
                                log.warn("    검색어: '{}' (일반 의류)", searchPart);
                                log.warn("    네이버 경로 최상위: '{}'", entryParts.length > 0 ? entryParts[0] : "N/A");
                                log.warn("    예상 매핑: 패션의류 또는 패션의류잡화");
                                log.warn("    실제 최상위가 스포츠/레저인 경우, 이는 의도된 동작입니다 (일반 의류는 스포츠 카테고리 제외)");
                                log.warn("    === 분석 완료 ===");
                            }
                            allMatched = false;
                            break;
                        }
                    } else {
                        // 중간/마지막 단계는 일반 매칭 (건너뛸 수 있는 중간 단계 허용)
                        // 마지막 단계인 경우, 네이버 경로의 마지막 부분부터 역순으로 확인
                        // 중간 단계인 경우, entryIdx부터 순차적으로 확인
                        log.info("  [단계 {}] 중간/마지막 단계 매칭 시도: '{}'", searchIdx, searchPart);
                        
                        // 마지막 단계인 경우 역순으로 확인 (마지막 부분 우선)
                        int startIdx = entryIdx;
                        int endIdx = entryParts.length;
                        int step = 1;
                        
                        if (searchIdx == searchParts.length - 1) {
                            // 마지막 단계: 마지막 부분부터 역순으로 확인
                            startIdx = entryParts.length - 1;
                            endIdx = entryIdx - 1;
                            step = -1;
                            log.debug("    마지막 단계이므로 역순 검색: startIdx={}, endIdx={}", startIdx, endIdx);
                        }
                        
                        for (int i = startIdx; (step > 0 ? i < endIdx : i > endIdx); i += step) {
                            String entryPart = entryParts[i];
                            log.debug("    entryParts[{}]: '{}' 확인 중...", i, entryPart);
                            
                            // 건너뛸 수 있는 중간 단계인지 확인
                            boolean isSkippable = false;
                            for (String skippable : skippableKeywords) {
                                if (entryPart.contains(skippable)) {
                                    isSkippable = true;
                                    log.debug("      건너뛸 수 있는 단계: '{}' (키워드: '{}')", entryPart, skippable);
                                    break;
                                }
                            }
                            
                            // 건너뛸 수 있는 중간 단계면 스킵하고 다음 단계 확인
                            if (isSkippable) {
                                continue;
                            }
                            
                            // 정확히 일치하는지 확인
                            if (entryPart.equals(searchPart)) {
                                log.info("    [단계 {}] 정확 일치: '{}' == '{}'", searchIdx, searchPart, entryPart);
                                matched = true;
                                entryIdx = i + 1;
                                log.info("    entryIdx를 {}로 업데이트", entryIdx);
                                // 건너뛸 수 있는 중간 단계들을 건너뛰기
                                while (entryIdx < entryParts.length) {
                                    String nextPart = entryParts[entryIdx];
                                    boolean nextIsSkippable = false;
                                    for (String skippable : skippableKeywords) {
                                        if (nextPart.contains(skippable)) {
                                            nextIsSkippable = true;
                                            break;
                                        }
                                    }
                                    if (!nextIsSkippable) {
                                        log.debug("      건너뛸 수 없는 단계 도달: '{}'", nextPart);
                                        break; // 건너뛸 수 없는 단계에 도달
                                    }
                                    log.debug("      건너뛰기: '{}'", nextPart);
                                    entryIdx++; // 건너뛰기
                                }
                                log.info("    건너뛰기 완료, 최종 entryIdx: {}", entryIdx);
                                break;
                            }
                            
                            // 포함 관계 확인 (예: "상의"와 "남성의류 > 상의")
                            if (entryPart.contains(searchPart) || searchPart.contains(entryPart)) {
                                log.debug("    포함 관계 확인: '{}' <-> '{}'", searchPart, entryPart);
                                // 엑셀 경로에 제외 키워드가 없을 때만 네이버 경로의 제외 키워드를 필터링
                                if (!searchPathHasExcludeKeyword) {
                                    boolean hasExcludeKeyword = false;
                                    for (String keyword : excludeKeywords) {
                                        if (entryPart.contains(keyword)) {
                                            hasExcludeKeyword = true;
                                            log.debug("      제외 키워드 발견: '{}' (키워드: '{}')", entryPart, keyword);
                                            break;
                                        }
                                    }
                                    if (hasExcludeKeyword) {
                                        continue; // 제외 키워드가 포함된 경로는 스킵
                                    }
                                }
                                log.info("    [단계 {}] 포함 관계 매칭 성공: '{}' <-> '{}'", searchIdx, searchPart, entryPart);
                                matched = true;
                                entryIdx = i + 1;
                                log.info("    entryIdx를 {}로 업데이트", entryIdx);
                                // 건너뛸 수 있는 중간 단계들을 건너뛰기
                                while (entryIdx < entryParts.length) {
                                    String nextPart = entryParts[entryIdx];
                                    boolean nextIsSkippable = false;
                                    for (String skippable : skippableKeywords) {
                                        if (nextPart.contains(skippable)) {
                                            nextIsSkippable = true;
                                            break;
                                        }
                                    }
                                    if (!nextIsSkippable) {
                                        log.debug("      건너뛸 수 없는 단계 도달: '{}'", nextPart);
                                        break; // 건너뛸 수 없는 단계에 도달
                                    }
                                    log.debug("      건너뛰기: '{}'", nextPart);
                                    entryIdx++; // 건너뛰기
                                }
                                log.info("    건너뛰기 완료, 최종 entryIdx: {}", entryIdx);
                                break;
                            }
                        }
                        
                        if (!matched) {
                            // "상의", "하의" 같은 일반 카테고리는 네이버 경로에 없을 수 있음
                            // 예: "의류 > 상의 > 후드티" -> "패션의류 > 여성의류 > 후드티셔츠" (상의 단계 없음)
                            // 이런 경우 해당 단계를 건너뛰고 다음 단계로 진행
                            boolean isSkippableGeneralCategory = false;
                            for (String skippable : skippableGeneralCategories) {
                                if (searchPart.equals(skippable)) {
                                    isSkippableGeneralCategory = true;
                                    log.info("  [단계 {}] 일반 카테고리 건너뛰기: '{}' (네이버 경로에 없을 수 있음)", 
                                            searchIdx, searchPart);
                                    matched = true; // 건너뛰기로 처리하여 다음 단계로 진행
                                    // entryIdx는 그대로 유지 (다음 단계에서 계속 검색)
                                    break;
                                }
                            }
                            
                            if (!isSkippableGeneralCategory) {
                                // 마지막 검색 부분인 경우, 네이버 경로의 마지막 부분과도 매칭 시도
                                if (searchIdx == searchParts.length - 1) {
                                    // entryLastPart는 이미 위에서 정의되어 있음
                                    String entryLastPartForMatch = entryParts[entryParts.length - 1].trim();
                                    
                                    // 마지막 부분 매칭 시도 (이미 마지막 부분 매칭 로직에서 확인했지만, 여기서도 재확인)
                                    boolean lastPartMatchesForStep = false;
                                    
                                    // 기본 매칭
                                    if (entryLastPartForMatch.equals(searchPart) 
                                        || entryLastPartForMatch.contains(searchPart) 
                                        || searchPart.contains(entryLastPartForMatch)) {
                                        lastPartMatchesForStep = true;
                                    }
                                    
                                    // 특수 케이스: "후드티" -> "후드집업", "후드" 등
                                    if (!lastPartMatchesForStep && searchPart.equals("후드티")) {
                                        if (entryLastPartForMatch.contains("후드") || entryLastPartForMatch.contains("후디")) {
                                            lastPartMatchesForStep = true;
                                            log.debug("    특수 케이스 매칭: '후드티' -> '{}' (후드 관련)", entryLastPartForMatch);
                                        }
                                    }
                                    
                                    // 특수 케이스: "반팔티" -> "티셔츠", "반팔티셔츠" 등
                                    if (!lastPartMatchesForStep && searchPart.equals("반팔티")) {
                                        if (entryLastPartForMatch.contains("반팔") || entryLastPartForMatch.contains("티셔츠")) {
                                            lastPartMatchesForStep = true;
                                            log.debug("    특수 케이스 매칭: '반팔티' -> '{}' (반팔/티셔츠 관련)", entryLastPartForMatch);
                                        }
                                    }
                                    
                                    // 특수 케이스: "로우탑" -> "스니커즈"
                                    if (!lastPartMatchesForStep && searchPart.equals("로우탑")) {
                                        if (entryLastPartForMatch.contains("스니커즈")) {
                                            lastPartMatchesForStep = true;
                                            log.debug("    특수 케이스 매칭: '로우탑' -> '{}' (스니커즈 관련)", entryLastPartForMatch);
                                        }
                                    }
                                    
                                    if (lastPartMatchesForStep) {
                                        log.info("  [단계 {}] 마지막 부분 매칭 성공: '{}' -> '{}'", 
                                                searchIdx, searchPart, entryLastPartForMatch);
                                        matched = true;
                                        // allMatched는 true로 유지
                                    } else {
                                        log.warn("  [단계 {}] 단계 매칭 실패: '{}' (entryIdx={}, entryParts.length={})", 
                                                searchIdx, searchPart, entryIdx, entryParts.length);
                                        log.warn("    entryParts: {}", java.util.Arrays.toString(entryParts));
                                        log.warn("    마지막 부분: '{}'", entryLastPartForMatch);
                                        allMatched = false;
                                        break;
                                    }
                                } else {
                                    log.warn("  [단계 {}] 단계 매칭 실패: '{}' (entryIdx={}, entryParts.length={})", 
                                            searchIdx, searchPart, entryIdx, entryParts.length);
                                    log.warn("    entryParts: {}", java.util.Arrays.toString(entryParts));
                                    allMatched = false;
                                    break;
                                }
                            }
                        }
                    }
                }
                log.info("=== 순차 매칭 완료: allMatched={}, searchIdx={}, searchParts.length={} ===", 
                        allMatched, searchIdx, searchParts.length);
                
                // 모든 단계가 순차적으로 매칭되었고, 마지막 부분도 일치하는 경우
                if (allMatched && searchIdx == searchParts.length) {
                    // 엑셀 경로에 제외 키워드가 없을 때만 네이버 경로의 제외 키워드를 필터링
                    // 엑셀 경로에 제외 키워드가 있으면 해당 카테고리를 판매하는 것이므로 매칭 허용
                    boolean shouldExclude = false;
                    if (!searchPathHasExcludeKeyword) {
                        // 엑셀 경로에 제외 키워드가 없으면, 네이버 경로에 제외 키워드가 있으면 제외
                        for (String keyword : excludeKeywords) {
                            if (entryKey.contains(keyword)) {
                                shouldExclude = true;
                                log.debug("순차 매칭 후보 제외 (엑셀 경로에 제외 키워드 없음, 네이버 경로에 제외 키워드 포함): '{}'", entryKey);
                                break;
                            }
                        }
                    }
                    // 엑셀 경로에 제외 키워드가 있으면 네이버 경로의 제외 키워드와 관계없이 매칭 허용
                    
                    if (!shouldExclude) {
                        log.debug("순차 매칭 후보 발견: '{}' -> '{}' (ID: {})", trimmedPath, entryKey, entry.getValue());
                        candidateEntries.add(entry);
                    }
                }
            }
            
            log.debug("순차 매칭 후보: {}개", candidateEntries.size());
            if (candidateEntries.isEmpty()) {
                log.warn("순차 매칭 후보가 없습니다. 검색 경로: '{}', 마지막 부분: '{}'", trimmedPath, lastPart);
                // 관련 카테고리 중 일부 출력
                if (!relatedCategories.isEmpty()) {
                    log.warn("관련 카테고리 예시 (처음 10개): {}", relatedCategories.subList(0, Math.min(10, relatedCategories.size())));
                }
                // 디버깅: 첫 번째 단계 매칭 실패 원인 분석
                if (searchParts.length > 0 && topCategoryMapping.containsKey(searchParts[0])) {
                    String[] mappedCategories = topCategoryMapping.get(searchParts[0]);
                    log.warn("=== 첫 번째 단계 매칭 실패 분석 ===");
                    log.warn("검색어 첫 번째 단계: '{}'", searchParts[0]);
                    log.warn("매핑 후보: {}", java.util.Arrays.toString(mappedCategories));
                    log.warn("마지막 부분과 일치하는 카테고리 중 최상위 카테고리 확인:");
                    int count = 0;
                for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                        String entryKey = entry.getKey();
                        String[] entryParts = entryKey.split("[>]");
                        if (entryParts.length > 0) {
                            String entryLastPart = entryParts[entryParts.length - 1].trim();
                            if (entryLastPart.contains(lastPart) || lastPart.contains(entryLastPart)) {
                                String topCategory = entryParts[0].trim();
                                log.warn("  카테고리: '{}' -> 최상위: '{}'", entryKey, topCategory);
                                count++;
                                if (count >= 20) break;
                            }
                        }
                    }
                    log.warn("=== 분석 완료 ===");
                }
            } else {
                log.debug("순차 매칭 후보 목록:");
                for (int i = 0; i < Math.min(10, candidateEntries.size()); i++) {
                    Map.Entry<String, String> candidate = candidateEntries.get(i);
                    log.debug("  후보 {}: '{}' -> ID: {}", i + 1, candidate.getKey(), candidate.getValue());
                }
            }
            
            // 후보 중에서 가장 적합한 것 선택 (경로 길이와 일치도 고려)
            if (!candidateEntries.isEmpty()) {
                Map.Entry<String, String> bestMatch = null;
                int bestScore = 0;
                
                for (Map.Entry<String, String> entry : candidateEntries) {
                    String entryKey = entry.getKey();
                    String[] entryParts = entryKey.split("[>]");
                    int score = 0;
                    
                    // 최상위 카테고리 우선순위 점수
                    // 스포츠 키워드가 있으면 스포츠/레저도 높은 점수, 없으면 패션의류만 높은 점수
                    if (entryParts.length > 0) {
                        String topCategory = entryParts[0].trim();
                        if (searchParts.length > 0) {
                            String firstSearchPart = searchParts[0].trim();
                            if (firstSearchPart.equals("의류") || firstSearchPart.equals("상의") || firstSearchPart.equals("하의")) {
                                if (searchPathHasSportKeyword) {
                                    // 스포츠 키워드가 있으면 스포츠/레저도 높은 점수
                                    if (topCategory.contains("스포츠") || topCategory.contains("레저")) {
                                        score += 50; // 스포츠 키워드가 있으면 스포츠/레저도 높은 점수
                                    } else if (topCategory.contains("패션의류")) {
                                        score += 30; // 패션의류도 허용하지만 낮은 점수
                                    }
                                } else {
                                    // 스포츠 키워드가 없으면 패션의류만 높은 점수
                                    if (topCategory.contains("패션의류")) {
                                        score += 50; // 매우 높은 우선순위
                                    } else if (topCategory.contains("스포츠") || topCategory.contains("레저")) {
                                        score -= 100; // 스포츠/레저는 제외 (음수로 낮은 점수)
                                    }
                                }
                            } else if (firstSearchPart.equals("신발")) {
                                // 신발은 "패션잡화"로 시작하는 경로에 높은 점수
                                if (topCategory.contains("패션잡화")) {
                                    score += 50;
                                } else if (topCategory.contains("스포츠") || topCategory.contains("레저")) {
                                    score -= 50; // 스포츠 신발은 낮은 점수 (하지만 완전히 제외하지는 않음)
                                }
                            }
                        }
                    }
                    
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
                    int searchIdx = 0;
                    int entryIdx = 0;
                    
                    while (searchIdx < searchParts.length && entryIdx < entryParts.length) {
                        String searchPart = searchParts[searchIdx].trim();
                        String entryPart = entryParts[entryIdx].trim();
                        
                        if (entryPart.equals(searchPart)) {
                            score += 10;
                            searchIdx++;
                            entryIdx++;
                        } else if (entryPart.contains(searchPart) || searchPart.contains(entryPart)) {
                            score += 5;
                            searchIdx++;
                            entryIdx++;
                        } else {
                            entryIdx++;
                        }
                    }
                    
                    // 음수 점수(제외 대상)는 선택하지 않음
                    if (score > bestScore && score > 0) {
                        bestScore = score;
                        bestMatch = entry;
                        log.debug("  후보 점수 업데이트: '{}' -> 점수: {}", entryKey, score);
                    } else if (score <= 0) {
                        log.debug("  후보 제외 (점수 <= 0): '{}' -> 점수: {}", entryKey, score);
                    }
                }
                
                if (bestMatch != null) {
                    log.info("카테고리 매핑 성공 (순차 경로 매칭): '{}' -> '{}' (ID: {}, 점수: {})", 
                            trimmedPath, bestMatch.getKey(), bestMatch.getValue(), bestScore);
                    return bestMatch.getValue();
                } else if (!candidateEntries.isEmpty()) {
                    log.warn("모든 후보가 제외되었습니다 (점수 <= 0). 검색 경로: '{}'", trimmedPath);
                }
            }
            
            log.debug("순차 경로 매칭 실패: '{}'", trimmedPath);
        }
        
        // 정확한 매핑이 없으면 null 반환하여 에러 발생시키기
        log.error("카테고리 매핑을 찾을 수 없습니다: '{}'. 엑셀 파일의 카테고리 컬럼을 숫자 ID로 변경하거나, 네이버 스마트스토어에서 확인한 정확한 카테고리 경로를 사용하세요.", trimmedPath);
        log.error("현재 매핑된 카테고리 개수: {}", categoryMappingCache.size());
        
        // 사용 가능한 카테고리 예시를 한 줄로 출력
        StringBuilder examples = new StringBuilder();
        examples.append("사용 가능한 카테고리 예시 (처음 50개): ");
        int count = 0;
        for (String key : categoryMappingCache.keySet()) {
            if (count > 0) {
                examples.append(" | ");
            }
            examples.append(key).append("=").append(categoryMappingCache.get(key));
            count++;
            if (count >= 50) {
                break;
            }
        }
        log.error("{}", examples.toString());
        
        // 검색어와 유사한 카테고리 찾기 (한 줄로)
        StringBuilder similar = new StringBuilder();
        similar.append("검색어 '").append(trimmedPath).append("'와 유사한 카테고리: ");
        String searchLower = trimmedPath.toLowerCase();
        count = 0;
        for (String key : categoryMappingCache.keySet()) {
            if (key.toLowerCase().contains(searchLower) || searchLower.contains(key.toLowerCase())) {
                if (count > 0) {
                    similar.append(", ");
                }
                similar.append(key).append("=").append(categoryMappingCache.get(key));
                count++;
                if (count >= 10) {
                    break;
                }
            }
        }
        if (count > 0) {
            log.error("{}", similar.toString());
        }
        return null;
    }
    
    /**
     * 재귀적으로 카테고리 트리에서 특정 키워드를 포함한 카테고리 찾기
     * 
     * @param categories 카테고리 리스트
     * @param keyword 검색 키워드
     * @param results 결과 리스트 (재귀 호출 시 사용)
     * @return 키워드를 포함한 카테고리 경로 리스트
     */
    @SuppressWarnings("unchecked")
    private List<String> findCategoriesByKeyword(List<Map<String, Object>> categories, String keyword, List<String> results) {
        if (categories == null || categories.isEmpty()) {
            return results;
        }
        
        for (Map<String, Object> category : categories) {
            Object wholeNameObj = category.get("wholeCategoryName");
            Object nameObj = category.get("name");
            Object idObj = category.get("id");
            
            String wholeName = wholeNameObj != null ? String.valueOf(wholeNameObj) : null;
            String name = nameObj != null ? String.valueOf(nameObj) : null;
            String id = idObj != null ? String.valueOf(idObj) : "N/A";
            
            // wholeCategoryName 또는 name에 키워드가 포함되어 있는지 확인
            if ((wholeName != null && wholeName.contains(keyword)) || 
                (name != null && name.contains(keyword))) {
                String displayPath = wholeName != null ? wholeName : name;
                results.add(displayPath + " -> " + id);
            }
            
            // 자식 카테고리 재귀 검색
            List<Map<String, Object>> children = (List<Map<String, Object>>) category.get("children");
            if (children != null && !children.isEmpty()) {
                findCategoriesByKeyword(children, keyword, results);
            }
        }
        
        return results;
    }
    
    /**
     * 카테고리 경로가 숫자 ID인지 확인
     * 
     * @param category 카테고리 경로 또는 ID
     * @return 숫자 ID이면 true, 아니면 false
     */
    public boolean isNumericCategoryId(String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        
        try {
            Long.parseLong(category.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

