package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayUserRepository extends JpaRepository<PayUser, Long> {

    // 외부 사용자 식별자 기반 조회
    Optional<PayUser> findByExternalUserId(Long externalUserId);
}
