package com.gorani.gorani_pay.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pay_idempotency")
@Getter
@Setter
@NoArgsConstructor
public class PayIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pay_payment_id")
    private Long payPaymentId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "external_order_id", nullable = false)
    private String externalOrderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    private String status;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}