package com.example.auto.coupang.client;

import com.example.auto.coupang.config.CoupangProperties;
import com.example.auto.coupang.constants.CoupangApiConstants;
import com.example.auto.coupang.util.CoupangSignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 쿠팡 오픈 API 클라이언트
 * <p>
 * API 문서: https://developers.coupangcorp.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoupangApiClient {

    private final WebClient coupangWebClient;
    private final CoupangProperties properties;

    /**
     * 공통 헤더 생성
     */
    private HttpHeaders createHeaders(String httpMethod, String path, String queryString, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Requested-By", properties.getVendorId());
        headers.set("X-Market", properties.getMarket());
        
        String authorization = CoupangSignatureUtil.generateAuthorizationHeader(
                properties.getAccessKey(),
                httpMethod,
                path,
                queryString,
                properties.getSecretKey(),
                body
        );
        headers.set("Authorization", authorization);
        
        return headers;
    }
    
    /**
     * 공통 헤더 생성 (body 없음)
     */
    private HttpHeaders createHeaders(String httpMethod, String path, String queryString) {
        return createHeaders(httpMethod, path, queryString, null);
    }

    /**
     * 쿼리 스트링 생성
     */
    private String buildQueryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        
        StringBuilder queryString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                queryString.append("&");
            }
            queryString.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                      .append("=")
                      .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return queryString.toString();
    }

    /**
     * 상품 목록 조회
     * 
     * @param page 페이지 번호 (1부터 시작)
     * @param size 페이지 크기
     * @return 상품 목록 응답
     */
    public Mono<Map<String, Object>> getProducts(Integer page, Integer size) {
        String path = CoupangApiConstants.Product.BASE;
        Map<String, String> params = Map.of(
                "vendorId", properties.getVendorId(),
                "page", String.valueOf(page != null ? page : 1),
                "size", String.valueOf(size != null ? size : CoupangApiConstants.DEFAULT_PAGE_SIZE)
        );
        String queryString = buildQueryString(params);
        
        String fullUrl = properties.getApiBaseUrl() + path + "?" + queryString;
        log.info("상품 목록 조회 요청: URL={}, page={}, size={}", fullUrl, page, size);
        
        HttpHeaders headers = createHeaders("GET", path, queryString);
        
        return coupangWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("vendorId", properties.getVendorId())
                        .queryParam("page", page != null ? page : 1)
                        .queryParam("size", size != null ? size : CoupangApiConstants.DEFAULT_PAGE_SIZE)
                        .build())
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(response -> log.info("상품 목록 조회 성공"))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("상품 목록 조회 실패: status={}, URL={}", ex.getStatusCode(), fullUrl);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("상품 목록 조회 실패", error);
                    }
                });
    }

    /**
     * 상품 상세 조회
     * 
     * @param productId 상품 ID
     * @return 상품 상세 정보
     */
    public Mono<Map<String, Object>> getProductDetail(String productId) {
        String path = CoupangApiConstants.Product.DETAIL.replace("{productId}", productId);
        Map<String, String> params = Map.of("vendorId", properties.getVendorId());
        String queryString = buildQueryString(params);
        
        String fullUrl = properties.getApiBaseUrl() + path + "?" + queryString;
        log.info("상품 상세 조회 요청: URL={}, productId={}", fullUrl, productId);
        
        HttpHeaders headers = createHeaders("GET", path, queryString);
        
        return coupangWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(CoupangApiConstants.Product.DETAIL)
                        .queryParam("vendorId", properties.getVendorId())
                        .build(productId))
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(response -> log.info("상품 상세 조회 성공: productId={}", productId))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("상품 상세 조회 실패: status={}, productId={}", ex.getStatusCode(), productId);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("상품 상세 조회 실패: productId={}", productId, error);
                    }
                });
    }

    /**
     * 상품 상태 변경
     * 
     * @param productId 상품 ID
     * @param status 상태 (SALE, OUT_OF_STOCK, SUSPENDED)
     * @return 응답 결과
     */
    public Mono<Map<String, Object>> updateProductStatus(String productId, String status) {
        String path = CoupangApiConstants.Product.STATUS.replace("{productId}", productId);
        Map<String, String> params = Map.of("vendorId", properties.getVendorId());
        String queryString = buildQueryString(params);
        
        String fullUrl = properties.getApiBaseUrl() + path + "?" + queryString;
        log.info("상품 상태 변경 요청: URL={}, productId={}, status={}", fullUrl, productId, status);
        
        HttpHeaders headers = createHeaders("PUT", path, queryString);
        
        Map<String, Object> requestBody = Map.of("status", status);
        
        return coupangWebClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path(CoupangApiConstants.Product.STATUS)
                        .queryParam("vendorId", properties.getVendorId())
                        .build(productId))
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(response -> log.info("상품 상태 변경 성공: productId={}, status={}", productId, status))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("상품 상태 변경 실패: status={}, productId={}, status={}", 
                                ex.getStatusCode(), productId, status);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("상품 상태 변경 실패: productId={}, status={}", productId, status, error);
                    }
                });
    }

    /**
     * 주문 목록 조회
     * 
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @param startDate 시작일 (yyyy-MM-dd 형식)
     * @param endDate 종료일 (yyyy-MM-dd 형식)
     * @return 주문 목록 응답
     */
    public Mono<Map<String, Object>> getOrders(Integer page, Integer size, String startDate, String endDate) {
        String path = CoupangApiConstants.Order.BASE;
        
        java.util.Map<String, String> paramsMap = new java.util.HashMap<>();
        paramsMap.put("vendorId", properties.getVendorId());
        paramsMap.put("page", String.valueOf(page != null ? page : 1));
        paramsMap.put("size", String.valueOf(size != null ? size : CoupangApiConstants.DEFAULT_PAGE_SIZE));
        if (startDate != null) {
            paramsMap.put("startDate", startDate);
        }
        if (endDate != null) {
            paramsMap.put("endDate", endDate);
        }
        String queryString = buildQueryString(paramsMap);
        
        String fullUrl = properties.getApiBaseUrl() + path + "?" + queryString;
        log.info("주문 목록 조회 요청: URL={}, page={}, size={}, startDate={}, endDate={}", 
                fullUrl, page, size, startDate, endDate);
        
        HttpHeaders headers = createHeaders("GET", path, queryString);
        
        var uriBuilder = coupangWebClient.get()
                .uri(uriBuilder1 -> {
                    var builder = uriBuilder1.path(path)
                            .queryParam("vendorId", properties.getVendorId())
                            .queryParam("page", page != null ? page : 1)
                            .queryParam("size", size != null ? size : CoupangApiConstants.DEFAULT_PAGE_SIZE);
                    if (startDate != null) {
                        builder.queryParam("startDate", startDate);
                    }
                    if (endDate != null) {
                        builder.queryParam("endDate", endDate);
                    }
                    return builder.build();
                });
        
        return uriBuilder
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(response -> log.info("주문 목록 조회 성공"))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("주문 목록 조회 실패: status={}, URL={}", ex.getStatusCode(), fullUrl);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("주문 목록 조회 실패", error);
                    }
                });
    }

    /**
     * 주문 상세 조회
     * 
     * @param orderId 주문 ID
     * @return 주문 상세 정보
     */
    public Mono<Map<String, Object>> getOrderDetail(String orderId) {
        String path = CoupangApiConstants.Order.DETAIL.replace("{orderId}", orderId);
        Map<String, String> params = Map.of("vendorId", properties.getVendorId());
        String queryString = buildQueryString(params);
        
        String fullUrl = properties.getApiBaseUrl() + path + "?" + queryString;
        log.info("주문 상세 조회 요청: URL={}, orderId={}", fullUrl, orderId);
        
        HttpHeaders headers = createHeaders("GET", path, queryString);
        
        return coupangWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(CoupangApiConstants.Order.DETAIL)
                        .queryParam("vendorId", properties.getVendorId())
                        .build(orderId))
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(response -> log.info("주문 상세 조회 성공: orderId={}", orderId))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("주문 상세 조회 실패: status={}, orderId={}", ex.getStatusCode(), orderId);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("주문 상세 조회 실패: orderId={}", orderId, error);
                    }
                });
    }

    /**
     * 상품 등록
     * 
     * @param productData 상품 등록 데이터
     * @return 등록된 상품 정보 (sellerProductId 포함)
     */
    public Mono<Map<String, Object>> createProduct(Map<String, Object> productData) {
        String path = CoupangApiConstants.Product.CREATE;
        String queryString = ""; // 상품 등록은 쿼리 파라미터 없음
        
        String fullUrl = properties.getApiBaseUrl() + path;
        
        // 요청 본문을 JSON 문자열로 변환 (서명 생성용)
        String requestBodyJson = null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            requestBodyJson = objectMapper.writeValueAsString(productData);
            log.info("상품 등록 요청: URL={}, vendorId={}", fullUrl, properties.getVendorId());
            log.info("요청 본문 (전체): {}", requestBodyJson);
            
            // notices 필드 상세 로깅
            if (productData.containsKey("items")) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> items = (java.util.List<Map<String, Object>>) productData.get("items");
                if (items != null && !items.isEmpty()) {
                    for (int i = 0; i < items.size(); i++) {
                        Map<String, Object> item = items.get(i);
                        if (item.containsKey("notices")) {
                            Object noticesObj = item.get("notices");
                            log.info("Item[{}] notices 필드: {}", i, noticesObj);
                            if (noticesObj instanceof java.util.List) {
                                @SuppressWarnings("unchecked")
                                java.util.List<Map<String, Object>> notices = (java.util.List<Map<String, Object>>) noticesObj;
                                log.info("Item[{}] notices 개수: {}", i, notices.size());
                                for (int j = 0; j < Math.min(notices.size(), 5); j++) {
                                    log.info("Item[{}] notices[{}]: {}", i, j, notices.get(j));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("요청 본문 로깅 실패", e);
        }
        
        // POST 요청의 경우 body를 서명에 포함
        HttpHeaders headers = createHeaders("POST", path, queryString, requestBodyJson);
        
        return coupangWebClient.post()
                .uri(path)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(productData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(response -> {
                    // 응답 본문의 code 필드 확인
                    Object code = response.get("code");
                    if ("ERROR".equals(code)) {
                        String errorMessage = String.valueOf(response.get("message"));
                        log.error("상품 등록 실패: code={}, message={}", code, errorMessage);
                        return Mono.error(new RuntimeException("상품 등록 실패: " + errorMessage));
                    }
                    
                    // SUCCESS인 경우에만 성공 처리
                    if ("SUCCESS".equals(code) || response.containsKey("data")) {
                        log.info("상품 등록 성공: {}", response);
                        if (response.containsKey("data")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = (Map<String, Object>) response.get("data");
                            if (data != null && data.containsKey("sellerProductId")) {
                                log.info("등록된 상품 ID: {}", data.get("sellerProductId"));
                            }
                        }
                        return Mono.just(response);
                    }
                    
                    // 알 수 없는 응답 형식
                    log.warn("알 수 없는 응답 형식: {}", response);
                    return Mono.just(response);
                })
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("상품 등록 실패: status={}, URL={}", ex.getStatusCode(), fullUrl);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                        
                        // 에러 응답 파싱 시도
                        if (responseBody != null && !responseBody.isEmpty()) {
                            try {
                                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                Map<String, Object> errorResponse = objectMapper.readValue(
                                        responseBody,
                                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                                );
                                
                                log.error("에러 코드: {}", errorResponse.get("code"));
                                log.error("에러 메시지: {}", errorResponse.get("message"));
                                
                                if (errorResponse.containsKey("errors")) {
                                    Object errors = errorResponse.get("errors");
                                    log.error("에러 상세: {}", errors);
                                }
                            } catch (Exception parseError) {
                                log.debug("에러 응답 본문 JSON 파싱 실패 (일반 텍스트일 수 있음)", parseError);
                                log.error("에러 응답 (텍스트): {}", responseBody);
                            }
                        }
                    } else {
                        log.error("상품 등록 실패", error);
                    }
                });
    }

    /**
     * 카테고리 목록 조회
     * 
     * @return 카테고리 목록 응답
     */
    public Mono<Map<String, Object>> getCategories() {
        String path = CoupangApiConstants.Category.LIST;
        String queryString = "";
        
        String fullUrl = properties.getApiBaseUrl() + path;
        log.info("카테고리 목록 조회 요청: URL={}", fullUrl);
        
        HttpHeaders headers = createHeaders("GET", path, queryString);
        
        return coupangWebClient.get()
                .uri(fullUrl)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(response -> log.info("카테고리 목록 조회 성공: 총 {}개", 
                        response.containsKey("data") ? "응답 수신" : "데이터 없음"))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("카테고리 목록 조회 실패: status={}, URL={}", ex.getStatusCode(), fullUrl);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("카테고리 목록 조회 실패", error);
                    }
                });
    }

    /**
     * 카테고리 메타데이터 조회
     * 
     * @param displayCategoryCode 카테고리 코드
     * @return 카테고리 메타데이터 (필수 고시정보, 필수 옵션 등 포함)
     */
    public Mono<Map<String, Object>> getCategoryMetadata(Long displayCategoryCode) {
        String path = CoupangApiConstants.Category.METADATA.replace("{displayCategoryCode}", String.valueOf(displayCategoryCode));
        String queryString = "";
        
        String fullUrl = properties.getApiBaseUrl() + path;
        log.info("카테고리 메타데이터 조회 요청: URL={}, categoryCode={}", fullUrl, displayCategoryCode);
        
        HttpHeaders headers = createHeaders("GET", path, queryString);
        
        return coupangWebClient.get()
                .uri(fullUrl)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(response -> {
                    log.info("카테고리 메타데이터 조회 성공: categoryCode={}", displayCategoryCode);
                    log.debug("카테고리 메타데이터 응답 전체: {}", response);
                    if (response.containsKey("data")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) response.get("data");
                        if (data != null) {
                            log.debug("카테고리 메타데이터 data 키들: {}", data.keySet());
                            // 고시정보 관련 필드 확인
                            if (data.containsKey("requiredNotices")) {
                                log.debug("requiredNotices 필드 발견");
                            }
                            if (data.containsKey("notices")) {
                                log.debug("notices 필드 발견");
                            }
                            if (data.containsKey("noticeCategories")) {
                                log.debug("noticeCategories 필드 발견");
                            }
                        }
                    }
                })
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        log.error("카테고리 메타데이터 조회 실패: status={}, categoryCode={}, URL={}", 
                                ex.getStatusCode(), displayCategoryCode, fullUrl);
                        log.error("응답 본문: {}", ex.getResponseBodyAsString());
                    } else {
                        log.error("카테고리 메타데이터 조회 실패: categoryCode={}", displayCategoryCode, error);
                    }
                });
    }

    /**
     * 카테고리 추천
     * 
     * @param productName 상품명 (필수)
     * @param productDescription 상품 상세설명 (선택)
     * @param brand 브랜드 (선택)
     * @param attributes 상품 속성 정보 (선택)
     * @return 카테고리 추천 결과
     */
    public Mono<Map<String, Object>> predictCategory(String productName,
                                                     String productDescription,
                                                     String brand,
                                                     Map<String, String> attributes) {
        String path = CoupangApiConstants.Category.PREDICT;
        String queryString = "";
        
        // 요청 본문 생성
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("productName", productName);
        if (productDescription != null && !productDescription.trim().isEmpty()) {
            requestBody.put("productDescription", productDescription);
        }
        if (brand != null && !brand.trim().isEmpty()) {
            requestBody.put("brand", brand);
        }
        if (attributes != null && !attributes.isEmpty()) {
            requestBody.put("attributes", attributes);
        }
        
        // 요청 본문을 JSON 문자열로 변환 (서명 생성용)
        String requestBodyJson = null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            requestBodyJson = objectMapper.writeValueAsString(requestBody);
            log.debug("카테고리 추천 요청: 상품명={}", productName);
        } catch (Exception e) {
            log.debug("요청 본문 로깅 실패", e);
        }
        
        String fullUrl = properties.getApiBaseUrl() + path;
        log.info("카테고리 추천 요청: URL={}, 상품명={}", fullUrl, productName);
        
        HttpHeaders headers = createHeaders("POST", path, queryString, requestBodyJson);
        
        return coupangWebClient.post()
                .uri(fullUrl)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(response -> {
                    log.info("카테고리 추천 성공: {}", response);
                    if (response.containsKey("data")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) response.get("data");
                        if (data != null && data.containsKey("predictedCategoryId")) {
                            log.info("추천 카테고리 ID: {}, 이름: {}", 
                                    data.get("predictedCategoryId"), 
                                    data.get("predictedCategoryName"));
                        }
                    }
                })
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("카테고리 추천 실패: status={}, URL={}", ex.getStatusCode(), fullUrl);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("카테고리 추천 실패", error);
                    }
                });
    }
}
