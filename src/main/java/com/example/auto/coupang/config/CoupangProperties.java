package com.example.auto.coupang.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 쿠팡 오픈 API 설정 프로퍼티
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "coupang.api")
public class CoupangProperties {
    
    private String vendorId;
    private String vendorUserId; // 실사용자 아이디 (Wing 사용자) - vendorId와 다를 수 있음
    private String accessKey;
    private String secretKey;
    private String apiBaseUrl = "https://api-gateway.coupang.com";
    private String market = "KR";
    private Long outboundShippingPlaceCode; // 출고지 주소 코드 (쿠팡 판매자 센터에서 등록한 주소록의 코드)
}
