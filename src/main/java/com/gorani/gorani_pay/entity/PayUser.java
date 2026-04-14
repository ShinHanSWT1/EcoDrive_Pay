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

// 결제 사용자
@Entity
@Table(name = "pay_users")
@Getter
@Setter
@NoArgsConstructor
public class PayUser {

    // 결제 사용자 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 외부 서비스 사용자 식별자
    @Column(name = "external_user_id", nullable = false)
    private Long externalUserId;

    // 결제 사용자 이름
    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    // 결제 사용자 이메일
    @Column(name = "email", nullable = false, length = 100)
    private String email;

    // 결제 사용자 상태
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    // 결제 사용자 생성시각
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 결제 사용자 수정시각
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
