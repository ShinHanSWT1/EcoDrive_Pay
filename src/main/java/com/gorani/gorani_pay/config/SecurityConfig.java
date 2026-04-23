package com.gorani.gorani_pay.config;

import com.gorani.gorani_pay.auth.oauth.PayOAuth2FailureHandler;
import com.gorani.gorani_pay.auth.oauth.PayOAuth2SuccessHandler;
import com.gorani.gorani_pay.security.InternalTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final InternalTokenFilter internalTokenFilter;
    private final PayOAuth2SuccessHandler payOAuth2SuccessHandler;
    private final PayOAuth2FailureHandler payOAuth2FailureHandler;

    public SecurityConfig(
            InternalTokenFilter internalTokenFilter,
            PayOAuth2SuccessHandler payOAuth2SuccessHandler,
            PayOAuth2FailureHandler payOAuth2FailureHandler
    ) {
        this.internalTokenFilter = internalTokenFilter;
        this.payOAuth2SuccessHandler = payOAuth2SuccessHandler;
        this.payOAuth2FailureHandler = payOAuth2FailureHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173", "http://localhost:9999", "https://dev-gorani.lab.terminal-lab.kr", "https://eri.gorani.shinhanacademy.co.kr", "https://www-gorani.lab.terminal-lab.kr"));
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(true);
                    return config;
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/error/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/pay/webhooks/**").permitAll()
                        .requestMatchers("/pay/login", "/pay/pay-login", "/pay/auth/**").permitAll()
                        .requestMatchers("/pay/oauth2/**", "/pay/login/oauth2/**").permitAll()
                        .requestMatchers("/pay/checkout/**").permitAll()
                        .requestMatchers("/pay/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/pay/oauth2/authorization")
                        )
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri("/pay/login/oauth2/code/*")
                        )
                        .successHandler(payOAuth2SuccessHandler)
                        .failureHandler(payOAuth2FailureHandler)
                )
                .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
