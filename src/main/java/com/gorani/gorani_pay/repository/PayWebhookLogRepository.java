package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayWebhookLogRepository extends JpaRepository<PayWebhookLog, Long> {

    List<PayWebhookLog> findByExternalOrderIdOrderByIdDesc(String externalOrderId);

    List<PayWebhookLog> findAllByOrderByIdDesc();
}
