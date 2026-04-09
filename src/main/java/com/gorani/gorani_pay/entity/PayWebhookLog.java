package com.gorani.gorani_pay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pay_webhook_logs")
@Getter
@Setter
@NoArgsConstructor
public class PayWebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "external_order_id", length = 50)
    private String externalOrderId;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(nullable = false, length = 20)
    private String status = "SUCCESS";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
