package com.gorani.gorani_pay.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 멱등성 관리 서비스
 * 기존 DB(RDB) 방식에서 Redis 방식으로 전환하여 성능을 높이고 자동 만료 기능을 활용합니다.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String IDEMPOTENCY_PREFIX = "idem:";

    /**
     * 기존 요청 확인 (Redis 조회)
     * @param key 멱등성 키
     * @return 저장된 응답 페이로드 (String)
     */
    public Optional<String> findByKey(String key) {
        Object value = redisTemplate.opsForValue().get(IDEMPOTENCY_PREFIX + key);
        return Optional.ofNullable((String) value);
    }

    public void save(
            String key,
            String externalOrderId,
            String responsePayload,
            String status
    ) {
        // 기존 엔티티 생성 및 DB 저장 로직을 Redis 저장 로직으로 교체합니다.
        // 페이로드 자체를 Redis에 저장하며, 24시간 후 자동 만료되도록 설정합니다.
        redisTemplate.opsForValue().set(
                IDEMPOTENCY_PREFIX + key,
                responsePayload,
                24, TimeUnit.HOURS
        );
    }
}