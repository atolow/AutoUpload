package com.example.auto.service;

import com.example.auto.naver.client.NaverCommerceClient;
import com.example.auto.domain.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OAuthService {

    private final NaverCommerceClient naverCommerceClient;
    private final StoreService storeService;

    /**
     * 네이버 커머스 API 인증 처리 (자동 스토어 갱신 또는 신규 생성)
     * 기존 스토어가 있으면 자동으로 토큰 갱신, 없으면 신규 생성
     *
     * @param type      인증 토큰 발급 타입 (SELF 또는 SELF)
     * @param accountId 판매자 ID (type이 SELF 경우 필수, 스토어가 없을 때만)
     * @param vendorId  판매자 ID (null이면 채널 정보에서 조회)
     * @param storeName 스토어 이름 (null이면 기본값 사용)
     * @return 생성/업데이트된 Store 엔티티
     */
    public Store authenticate(String type, String accountId, String vendorId, String storeName) {
        // 기존 스토어가 있으면 자동 토큰 갱신
        java.util.Optional<Store> existingStore = storeService.getCurrentStore();
        
        if (existingStore.isPresent()) {
            Store store = existingStore.get();
            log.info("기존 스토어 발견, 자동 토큰 갱신: vendorId={}", store.getVendorId());
            
            // 저장된 vendorId를 accountId로 사용 (기본값)
            String autoAccountId = store.getVendorId();
            String autoType = type != null ? type : "SELF"; // 기본값 SELF
            
            return authenticateAndCreateStore(
                    autoType, 
                    autoAccountId, 
                    store.getVendorId(), 
                    store.getStoreName()
            );
        } else {
            // 스토어가 없으면 신규 생성 (필수 정보 검증)
            String finalTypeValue = type != null ? type : "SELF"; // 기본값 SELF
            
            if ("SELF".equals(finalTypeValue) && (accountId == null || accountId.isEmpty())) {
                throw new IllegalArgumentException("최초 설정: type이 SELF인 경우 accountId는 필수입니다.");
            }
            
            return authenticateAndCreateStore(finalTypeValue, accountId, vendorId, storeName);
        }
    }
    
    /**
     * 네이버 커머스 API 인증 + 스토어 생성/업데이트
     *
     * @param type      인증 토큰 발급 타입 (SELF 또는 SELF)
     * @param accountId 판매자 ID (type이 SELF 경우 필수)
     * @param vendorId  판매자 ID (null이면 채널 정보에서 조회)
     * @param storeName 스토어 이름 (null이면 기본값 사용)
     * @return 생성/업데이트된 Store 엔티티
     */
    public Store authenticateAndCreateStore(String type, String accountId, String vendorId, String storeName) {
        log.info("네이버 커머스 API 인증 시작: type={}, accountId={}, vendorId={}, storeName={}",
                type, accountId, vendorId, storeName);

        // 1) Access Token 발급
        Map<String, Object> tokenResponse = naverCommerceClient
                .getAccessToken(type, accountId)
                .block();

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new IllegalStateException("네이버 커머스 Access Token 발급 실패: 응답 없음");
        }

        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = tokenResponse.containsKey("refresh_token") 
                ? (String) tokenResponse.get("refresh_token") 
                : null;
        
        // expires_in (초 단위)을 LocalDateTime으로 변환
        LocalDateTime tokenExpiresAt = null;
        if (tokenResponse.containsKey("expires_in")) {
            Object expiresInObj = tokenResponse.get("expires_in");
            long expiresInSeconds = expiresInObj instanceof Number 
                    ? ((Number) expiresInObj).longValue() 
                    : Long.parseLong(expiresInObj.toString());
            tokenExpiresAt = LocalDateTime.now().plusSeconds(expiresInSeconds);
        }

        log.info("네이버 커머스 Access Token 발급 성공: expiresAt={}", tokenExpiresAt);

        // 2) vendorId 결정 로직
        String finalVendorId = vendorId;
        String finalStoreName = storeName;
        
        // vendorId가 없으면 accountId를 vendorId로 사용 (SELF 타입의 경우 일반적)
        if (finalVendorId == null || finalVendorId.isEmpty()) {
            if (accountId != null && !accountId.isEmpty()) {
                log.info("vendorId가 없어 accountId를 vendorId로 사용: {}", accountId);
                finalVendorId = accountId;
            } else {
                // accountId도 없으면 채널 정보에서 조회 시도 (선택적)
                try {
                    Map<String, Object> channelsResponse = naverCommerceClient
                            .getUserInfo(accessToken)
                            .block();

                    log.info("네이버 커머스 채널 정보: {}", channelsResponse);

                    // channelsResponse에서 vendorId 추출
                    if (channelsResponse != null && channelsResponse.containsKey("channels")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> channels = (List<Map<String, Object>>) channelsResponse.get("channels");
                        if (channels != null && !channels.isEmpty()) {
                            Map<String, Object> firstChannel = channels.get(0);
                            if (firstChannel.containsKey("vendorId")) {
                                finalVendorId = String.valueOf(firstChannel.get("vendorId"));
                            }
                            if (finalStoreName == null && firstChannel.containsKey("channelName")) {
                                finalStoreName = String.valueOf(firstChannel.get("channelName"));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("채널 정보 조회 실패 (404 등), accountId를 vendorId로 사용: {}", accountId, e);
                    // 채널 정보 조회 실패 시에도 accountId가 있으면 사용
                    if (accountId != null && !accountId.isEmpty()) {
                        finalVendorId = accountId;
                    }
                }
            }
        }

        if (finalVendorId == null || finalVendorId.isEmpty()) {
            throw new IllegalStateException("vendorId를 확인할 수 없습니다. vendorId 또는 accountId를 입력하세요.");
        }

        if (finalStoreName == null || finalStoreName.isEmpty()) {
            finalStoreName = "네이버 스마트스토어"; // 기본값
        }

        // 3) 기존 스토어가 있으면 업데이트, 없으면 생성
        Store store = storeService.getStoreByVendorId(finalVendorId)
                .orElse(null);

        if (store != null) {
            // 기존 스토어 업데이트
            store.setAccessToken(accessToken);
            if (refreshToken != null) {
                store.setRefreshToken(refreshToken);
            }
            if (tokenExpiresAt != null) {
                store.setTokenExpiresAt(tokenExpiresAt);
            }
            store.setStoreName(finalStoreName);
            store.setActive(true);
            
            log.info("스토어 업데이트 완료: vendorId={}, storeName={}", finalVendorId, finalStoreName);
            return storeService.updateStore(store.getId(), store);
        } else {
            // 새 스토어 생성 시도
            Store newStore = Store.builder()
                    .vendorId(finalVendorId)
                    .storeName(finalStoreName)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenExpiresAt(tokenExpiresAt)
                    .isActive(true)
                    .build();

            try {
                log.info("스토어 생성 시도: vendorId={}, storeName={}", finalVendorId, finalStoreName);
                return storeService.createStore(newStore);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // 중복 키 예외 발생 시 (동시성 문제 등) 기존 스토어를 조회하여 업데이트
                log.warn("스토어 생성 중 중복 키 예외 발생, 기존 스토어를 업데이트합니다: vendorId={}, error={}", 
                        finalVendorId, e.getMessage());
                
                // 다시 조회 시도 (람다 표현식에서 사용하기 위해 변수 복사)
                String vendorIdForLambda = finalVendorId;
                Exception exceptionForLambda = e;
                Store existingStore = storeService.getStoreByVendorId(finalVendorId)
                        .orElseThrow(() -> new IllegalStateException(
                                "스토어 생성 실패 후 조회도 실패했습니다: vendorId=" + vendorIdForLambda, exceptionForLambda));
                
                // 기존 스토어 업데이트
                existingStore.setAccessToken(accessToken);
                if (refreshToken != null) {
                    existingStore.setRefreshToken(refreshToken);
                }
                if (tokenExpiresAt != null) {
                    existingStore.setTokenExpiresAt(tokenExpiresAt);
                }
                existingStore.setStoreName(finalStoreName);
                existingStore.setActive(true);
                
                log.info("중복 키 예외 처리 후 스토어 업데이트 완료: vendorId={}, storeName={}", 
                        finalVendorId, finalStoreName);
                return storeService.updateStore(existingStore.getId(), existingStore);
            }
        }
    }
}
