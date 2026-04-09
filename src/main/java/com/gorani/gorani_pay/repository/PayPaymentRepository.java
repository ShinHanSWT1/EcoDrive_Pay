package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PayPaymentRepository extends JpaRepository<PayPayment, Long> {

    Optional<PayPayment> findByExternalOrderId(String externalOrderId);

    List<PayPayment> findByPayUserIdOrderByIdDesc(Long payUserId);

    List<PayPayment> findByStatusAndCreatedAtBefore(String status, LocalDateTime createdAt);
}
