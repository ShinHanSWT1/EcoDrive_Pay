package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayRefundRepository extends JpaRepository<PayRefund, Long> {

    List<PayRefund> findByPayPaymentIdOrderByIdDesc(Long payPaymentId);
}
