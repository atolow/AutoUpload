package com.example.auto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * 네이버 API 및 쿠팡 API 호출을 위한 WebClient 설정
 */
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .defaultHeader("Content-Type", "application/json");
    }
    
    /**
     * 네이버 커머스 API용 WebClient
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://api.commerce.naver.com")
                .build();
    }
    
    /**
     * 쿠팡 오픈 API용 WebClient
     */
    @Bean
    public WebClient coupangWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://api-gateway.coupang.com")
                .build();
    }
}

