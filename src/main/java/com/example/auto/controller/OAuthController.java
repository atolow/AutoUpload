package com.example.auto.controller;

import com.example.auto.domain.Store;
import com.example.auto.dto.AuthenticateRequest;
import com.example.auto.service.OAuthService;
import com.example.auto.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth 인증 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;

    private final StoreService storeService;

    /**
     * 네이버 커머스 API 인증 토큰 발급 및 스토어 등록
     * 전자서명 기반 인증 방식
     * <p>
     * 스토어가 이미 등록되어 있으면 저장된 정보를 자동으로 사용합니다.
     * 스토어가 없으면 최초 1회만 입력받습니다.
     * <p>
     * JSON Body, Form Data, Query Parameters 모두 지원합니다.
     * JSON Body가 있으면 우선 사용하고, 없으면 Form Data 또는 Query Parameters를 사용합니다.
     *
     * @param request   JSON Body (선택)
     * @param type      인증 토큰 발급 타입 (Query Parameter 또는 Form Data, 선택)
     * @param accountId 판매자 ID (Query Parameter 또는 Form Data, 선택)
     * @param vendorId  판매자 ID (Query Parameter 또는 Form Data, 선택)
     * @param storeName 스토어 이름 (Query Parameter 또는 Form Data, 선택)
     * @return 스토어 정보
     */
    @PostMapping(value = "/authenticate")
    public ResponseEntity<?> authenticate(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String vendorId,
            @RequestParam(required = false) String storeName) {

        try {
            Store store = oAuthService.authenticate(type, accountId, vendorId, storeName);
            return ResponseEntity.ok(store);

        } catch (IllegalArgumentException e) {
            log.error("네이버 커머스 API 인증 실패: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);

        } catch (Exception e) {
            log.error("네이버 커머스 API 인증 처리 중 오류 발생", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * 네이버 커머스 API 인증 토큰 발급 및 스토어 등록 (JSON Body 지원)
     * 전자서명 기반 인증 방식
     * <p>
     * JSON Body로 요청하는 경우 사용합니다.
     *
     * @param request 인증 요청 데이터 (JSON Body)
     * @return 스토어 정보
     */
    @PostMapping(value = "/authenticate/json", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> authenticateJson(@RequestBody AuthenticateRequest request) {
        return authenticate(
                request.getType(),
                request.getAccountId(),
                request.getVendorId(),
                request.getStoreName()
        );
    }
}

