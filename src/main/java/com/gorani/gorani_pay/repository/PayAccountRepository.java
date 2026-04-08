package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PayAccountRepository extends JpaRepository<PayAccount, Long> {

    // 동시성 제어 (결제 시 필수)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PayAccount> findByPayUserId(Long payUserId);
}