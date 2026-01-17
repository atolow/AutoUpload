package com.example.auto.coupang.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 쿠팡 오픈 API HMAC 서명 유틸리티
 * 
 * 쿠팡 API 인증 방식:
 * 1. timestamp 생성 (yyMMddHHmmssZ 형식)
 * 2. 서명 문자열 생성: timestamp + HTTP_METHOD + path + queryString
 * 3. HMAC SHA256으로 서명 생성
 * 4. Authorization 헤더 생성: CEA algorithm=HmacSHA256, access-key={accessKey}, signed-date={timestamp}, signature={signature}
 */
public class CoupangSignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd'T'HHmmss'Z'");

    private CoupangSignatureUtil() {
        // 유틸 클래스이므로 인스턴스 생성 방지
    }

    /**
     * 쿠팡 API용 타임스탬프 생성 (yyMMdd'T'HHmmss'Z' 형식)
     * 예: 260117T160405Z
     * 
     * @return 타임스탬프 문자열
     */
    public static String generateTimestamp() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return now.format(TIMESTAMP_FORMATTER);
    }

    /**
     * 쿠팡 API용 HMAC 서명 생성 (내부적으로 타임스탬프 생성)
     * 
     * @param httpMethod HTTP 메소드 (GET, POST, PUT, DELETE 등)
     * @param path       API 경로 (예: /v2/providers/seller_api/apis/api/v1/products)
     * @param queryString 쿼리 스트링 (예: vendorId=A01585853&page=1)
     * @param secretKey  Secret Key
     * @return HMAC SHA256 서명 (hex 문자열)
     */
    public static String generateSignature(String httpMethod, 
                                          String path, 
                                          String queryString, 
                                          String secretKey) {
        String timestamp = generateTimestamp();
        return generateSignatureWithTimestamp(httpMethod, path, queryString, secretKey, timestamp);
    }

    /**
     * Authorization 헤더 값 생성
     * 
     * 쿠팡 API 규칙:
     * - Body는 서명에 포함하지 않음
     * - 형식: CEA algorithm=HmacSHA256, access-key={accessKey}, signed-date={timestamp}, signature={signature}
     * 
     * @param accessKey  Access Key
     * @param httpMethod HTTP 메소드
     * @param path       API 경로
     * @param queryString 쿼리 스트링
     * @param secretKey  Secret Key
     * @param body       요청 본문 (사용하지 않음, 쿠팡 API는 body를 서명에 포함하지 않음)
     * @return Authorization 헤더 값
     */
    public static String generateAuthorizationHeader(String accessKey,
                                                     String httpMethod,
                                                     String path,
                                                     String queryString,
                                                     String secretKey,
                                                     String body) {
        String timestamp = generateTimestamp();
        // body는 서명에 포함하지 않음 (쿠팡 API 규칙)
        String signature = generateSignatureWithTimestamp(httpMethod, path, queryString, secretKey, timestamp, null);
        
        return String.format("CEA algorithm=HmacSHA256, access-key=%s, signed-date=%s, signature=%s",
                accessKey, timestamp, signature);
    }
    
    /**
     * Authorization 헤더 값 생성 (body 없음)
     */
    public static String generateAuthorizationHeader(String accessKey,
                                                     String httpMethod,
                                                     String path,
                                                     String queryString,
                                                     String secretKey) {
        return generateAuthorizationHeader(accessKey, httpMethod, path, queryString, secretKey, null);
    }

    /**
     * 지정된 타임스탬프를 사용하여 HMAC 서명 생성
     * 
     * 쿠팡 API 규칙:
     * - 서명 메시지: timestamp + HTTP_METHOD + path + queryString
     * - Body는 서명에 포함하지 않음 (POST/PUT 요청이라도)
     * 
     * @param httpMethod HTTP 메소드 (GET, POST, PUT, DELETE 등)
     * @param path       API 경로
     * @param queryString 쿼리 스트링
     * @param secretKey  Secret Key
     * @param timestamp  타임스탬프
     * @param body       요청 본문 (사용하지 않음, 쿠팡 API는 body를 서명에 포함하지 않음)
     * @return HMAC SHA256 서명 (hex 문자열)
     */
    public static String generateSignatureWithTimestamp(String httpMethod, 
                                                        String path, 
                                                        String queryString, 
                                                        String secretKey,
                                                        String timestamp,
                                                        String body) {
        try {
            // 서명할 문자열 생성: timestamp + HTTP_METHOD + path + queryString
            // 주의: Body는 서명에 포함하지 않음 (쿠팡 API 규칙)
            String dataToSign = timestamp + httpMethod.toUpperCase() + path;
            
            // 쿼리 스트링 추가 (있는 경우만)
            if (queryString != null && !queryString.isEmpty()) {
                dataToSign += queryString;
            }
            
            // Body는 서명에 포함하지 않음 (쿠팡 API 규칙)
            
            // HMAC SHA256 서명 생성
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), 
                HMAC_SHA256
            );
            mac.init(secretKeySpec);
            
            byte[] hashBytes = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            
            // Hex 문자열로 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC 서명 생성 실패", e);
        }
    }
    
    /**
     * 지정된 타임스탬프를 사용하여 HMAC 서명 생성 (body 없음)
     */
    public static String generateSignatureWithTimestamp(String httpMethod, 
                                                        String path, 
                                                        String queryString, 
                                                        String secretKey,
                                                        String timestamp) {
        return generateSignatureWithTimestamp(httpMethod, path, queryString, secretKey, timestamp, null);
    }
}
