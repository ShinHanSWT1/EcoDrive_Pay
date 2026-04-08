package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayIdempotencyRepository extends JpaRepository<PayIdempotency, Long> {

    Optional<PayIdempotency> findByIdempotencyKey(String idempotencyKey);
}