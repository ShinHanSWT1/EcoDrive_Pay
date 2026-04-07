package com.gorani.gorani_pay.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pay_accounts")
@Getter
@Setter
@NoArgsConstructor
public class PayAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pay_user_id", nullable = false)
    private Long payUserId;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "owner_name", nullable = false, length = 50)
    private String ownerName;

    @Column(nullable = false)
    private Integer balance = 0;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void addBalance(Integer amount) {
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void deductBalance(Integer amount) {
        if (this.balance < amount) throw new RuntimeException("잔액 부족");
        this.balance -= amount;
        this.updatedAt = LocalDateTime.now();
    }
}