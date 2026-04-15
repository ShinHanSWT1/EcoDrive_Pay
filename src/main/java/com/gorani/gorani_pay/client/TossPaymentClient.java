package com.gorani.gorani_pay.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private final RestTemplate restTemplate; // Bean으로 등록되어 있어야 합니다.

    @Value("${app.toss.secret-key}")
    private String secretKey;

    @Value("${app.toss.url}")
    private String tossUrl;

    public void confirmPayment(String paymentKey, String orderId, Integer amount) {
        // 1. 토스 API 인증 헤더 만들기 (Basic 인증: 시크릿키 뒤에 콜론(:)을 붙여 Base64 인코딩)
        String authString = secretKey + ":";
        String encodedAuth = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encodedAuth);

        // 2. 요청 바디 만들기
        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            // 3. 토스 서버로 결제 승인 요청 날리기
            ResponseEntity<String> response = restTemplate.postForEntity(tossUrl, requestEntity, String.class);
            log.info("토스 결제 승인 성공: {}", response.getBody());
        } catch (Exception e) {
            log.error("토스 결제 승인 실패", e);
            throw new RuntimeException("토스페이먼츠 결제 승인에 실패했습니다."); // 커스텀 예외로 변경 추천
        }
    }
}