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
@Table(name = "pay_payments")
@Getter
@Setter
@NoArgsConstructor
public class PayPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pay_user_id", nullable = false)
    private Long payUserId;

    @Column(name = "pay_account_id", nullable = false)
    private Long payAccountId;

    @Column(name = "external_order_id", nullable = false, unique = true, length = 50)
    private String externalOrderId;

    @Column(name = "payment_type", nullable = false, length = 20)
    private String paymentType;

    @Column(name = "pay_product_id")
    private Long payProductId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "point_amount", nullable = false)
    private Integer pointAmount = 0;

    @Column(name = "coupon_discount_amount", nullable = false)
    private Integer couponDiscountAmount = 0;

    @Column(nullable = false, length = 20)
    private String status = "READY";

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
