package com.gorani.gorani_pay.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pay_refunds")
@Getter
@Setter
@NoArgsConstructor
public class PayRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pay_payment_id", nullable = false)
    private Long payPaymentId;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    private String reason;

    @Column(nullable = false, length = 20)
    private String status = "REQUESTED";

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}