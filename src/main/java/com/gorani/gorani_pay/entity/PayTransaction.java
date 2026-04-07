package com.gorani.gorani_pay.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pay_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PayTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pay_account_id", nullable = false)
    private Long payAccountId;

    @Column(name = "pay_payment_id")
    private Long payPaymentId;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Column(nullable = false, length = 10)
    private String direction; // DEBIT / CREDIT

    @Column(nullable = false)
    private Integer amount;

    @Column(length = 30)
    private String category;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}