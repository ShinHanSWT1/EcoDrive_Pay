package com.gorani.gorani_pay.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PayAccountTest {

    private PayAccount account;

    @BeforeEach
    void setUp() {
        account = new PayAccount();
        account.setPayUserId(1L);
        account.setAccountNumber("1234567890");
        account.setOwnerName("테스트 유저");
    }

    @Test
    @DisplayName("계좌 기본값 확인 - balance=0, status=ACTIVE")
    void defaultValues() {
        PayAccount newAccount = new PayAccount();
        assertThat(newAccount.getBalance()).isEqualTo(0);
        assertThat(newAccount.getStatus()).isEqualTo("ACTIVE");
        assertThat(newAccount.getCreatedAt()).isNotNull();
        assertThat(newAccount.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("addBalance - 잔액이 증가한다")
    void addBalance_increasesBalance() {
        account.addBalance(5000);
        assertThat(account.getBalance()).isEqualTo(5000);
    }

    @Test
    @DisplayName("addBalance - 여러 번 충전하면 누적된다")
    void addBalance_accumulatesMultipleCharges() {
        account.addBalance(3000);
        account.addBalance(2000);
        assertThat(account.getBalance()).isEqualTo(5000);
    }

    @Test
    @DisplayName("addBalance - updatedAt이 갱신된다")
    void addBalance_updatesTimestamp() throws InterruptedException {
        java.time.LocalDateTime before = account.getUpdatedAt();
        Thread.sleep(10);
        account.addBalance(100);
        assertThat(account.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("deductBalance - 잔액이 차감된다")
    void deductBalance_decreasesBalance() {
        account.setBalance(10000);
        account.deductBalance(3000);
        assertThat(account.getBalance()).isEqualTo(7000);
    }

    @Test
    @DisplayName("deductBalance - 잔액과 동일한 금액 차감 가능 (경계값)")
    void deductBalance_exactBalance_succeeds() {
        account.setBalance(5000);
        account.deductBalance(5000);
        assertThat(account.getBalance()).isEqualTo(0);
    }

    @Test
    @DisplayName("deductBalance - 잔액 부족 시 RuntimeException 발생")
    void deductBalance_insufficientBalance_throwsException() {
        account.setBalance(1000);
        assertThatThrownBy(() -> account.deductBalance(2000))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("잔액 부족");
    }

    @Test
    @DisplayName("deductBalance - 잔액이 0인 상태에서 출금하면 예외 발생")
    void deductBalance_zeroBalance_throwsException() {
        account.setBalance(0);
        assertThatThrownBy(() -> account.deductBalance(1))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("잔액 부족");
    }

    @Test
    @DisplayName("deductBalance - updatedAt이 갱신된다")
    void deductBalance_updatesTimestamp() throws InterruptedException {
        account.setBalance(10000);
        java.time.LocalDateTime before = account.getUpdatedAt();
        Thread.sleep(10);
        account.deductBalance(1000);
        assertThat(account.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("addBalance 후 deductBalance - 순서대로 처리된다")
    void addThenDeduct_correctBalance() {
        account.addBalance(10000);
        account.deductBalance(3000);
        assertThat(account.getBalance()).isEqualTo(7000);
    }
}