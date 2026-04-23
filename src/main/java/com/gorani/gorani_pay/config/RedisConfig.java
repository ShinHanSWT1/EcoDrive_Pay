package com.gorani.gorani_pay.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:gorani}") // 비밀번호 주입 (기본값 gorani)
    private String password;

    // Redis 템플릿 빈 설정
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.json());

        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(RedisSerializer.json());

        return template;
    }

    // Redisson 클라이언트 빈 설정
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // redis://host:port 형식으로 주소를 설정하고 비밀번호를 추가합니다.
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setPassword(password); // [이 줄이 반드시 들어가야 합니다!]

        return Redisson.create(config);
    }
}
