package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayPaymentRepository extends JpaRepository<PayPayment, Long> {

    Optional<PayPayment> findByExternalOrderId(String externalOrderId);
}