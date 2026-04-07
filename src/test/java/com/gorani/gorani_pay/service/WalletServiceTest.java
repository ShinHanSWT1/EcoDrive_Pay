package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayTransaction;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import com.gorani.gorani_pay.repository.PayTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private PayAccountRepository accountRepository;

    @Mock
    private PayTransactionRepository transactionRepository;

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private WalletService walletService;

    private PayAccount account;

    @BeforeEach
    void setUp() {
        account = new PayAccount();
        account.setId(1L);
        account.setPayUserId(100L);
        account.setAccountNumber("111-222-333");
        account.setOwnerName("테스트");
        account.setBalance(10000);
    }

    // ── getAccount ──────────────────────────────────────────────

    @Test
    @DisplayName("getAccount - 계좌가 존재하면 반환한다")
    void getAccount_found_returnsAccount() {
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));

        PayAccount result = walletService.getAccount(100L);

        assertThat(result).isEqualTo(account);
    }

    @Test
    @DisplayName("getAccount - 계좌가 없으면 RuntimeException 발생")
    void getAccount_notFound_throwsException() {
        when(accountRepository.findByPayUserId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getAccount(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("계좌 없음");
    }

    // ── charge ──────────────────────────────────────────────────

    @Test
    @DisplayName("charge - 잔액이 증가하고 CREDIT 트랜잭션이 저장된다")
    void charge_increasesBalanceAndSavesTransaction() {
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));
        PayTransaction savedTx = new PayTransaction();
        savedTx.setId(10L);
        when(transactionRepository.save(any())).thenReturn(savedTx);

        PayAccount result = walletService.charge(100L, 5000);

        assertThat(result.getBalance()).isEqualTo(15000);

        ArgumentCaptor<PayTransaction> txCaptor = ArgumentCaptor.forClass(PayTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        PayTransaction tx = txCaptor.getValue();
        assertThat(tx.getTransactionType()).isEqualTo("CHARGE");
        assertThat(tx.getDirection()).isEqualTo("CREDIT");
        assertThat(tx.getAmount()).isEqualTo(5000);
        assertThat(tx.getPayAccountId()).isEqualTo(1L);
        assertThat(tx.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("charge - LedgerService.record 가 CREDIT 타입으로 호출된다")
    void charge_recordsLedgerWithCredit() {
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));
        PayTransaction savedTx = new PayTransaction();
        savedTx.setId(10L);
        when(transactionRepository.save(any())).thenReturn(savedTx);

        walletService.charge(100L, 3000);

        // balance after = 10000 + 3000 = 13000
        verify(ledgerService).record(
                eq(savedTx.getId()),
                eq(account.getId()),
                eq("CREDIT"),
                eq(3000),
                eq(13000),
                eq("CHARGE"),
                eq(account.getId())
        );
    }

    @Test
    @DisplayName("charge - 계좌가 없으면 RuntimeException 발생")
    void charge_accountNotFound_throwsException() {
        when(accountRepository.findByPayUserId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.charge(999L, 1000))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("계좌 없음");

        verify(transactionRepository, never()).save(any());
        verify(ledgerService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    // ── withdraw ─────────────────────────────────────────────────

    @Test
    @DisplayName("withdraw - 잔액이 차감되고 DEBIT 트랜잭션이 저장된다")
    void withdraw_decreasesBalanceAndSavesTransaction() {
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));
        PayTransaction savedTx = new PayTransaction();
        savedTx.setId(20L);
        when(transactionRepository.save(any())).thenReturn(savedTx);

        PayAccount result = walletService.withdraw(100L, 4000);

        assertThat(result.getBalance()).isEqualTo(6000);

        ArgumentCaptor<PayTransaction> txCaptor = ArgumentCaptor.forClass(PayTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        PayTransaction tx = txCaptor.getValue();
        assertThat(tx.getTransactionType()).isEqualTo("WITHDRAW");
        assertThat(tx.getDirection()).isEqualTo("DEBIT");
        assertThat(tx.getAmount()).isEqualTo(4000);
    }

    @Test
    @DisplayName("withdraw - LedgerService.record 가 DEBIT 타입으로 호출된다")
    void withdraw_recordsLedgerWithDebit() {
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));
        PayTransaction savedTx = new PayTransaction();
        savedTx.setId(20L);
        when(transactionRepository.save(any())).thenReturn(savedTx);

        walletService.withdraw(100L, 2000);

        // balance after = 10000 - 2000 = 8000
        verify(ledgerService).record(
                eq(savedTx.getId()),
                eq(account.getId()),
                eq("DEBIT"),
                eq(2000),
                eq(8000),
                eq("WITHDRAW"),
                eq(account.getId())
        );
    }

    @Test
    @DisplayName("withdraw - 잔액 부족 시 RuntimeException 발생하고 트랜잭션이 저장되지 않는다")
    void withdraw_insufficientBalance_throwsAndNoTransaction() {
        account.setBalance(500);
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> walletService.withdraw(100L, 1000))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("잔액 부족");

        verify(transactionRepository, never()).save(any());
        verify(ledgerService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("withdraw - 계좌가 없으면 RuntimeException 발생")
    void withdraw_accountNotFound_throwsException() {
        when(accountRepository.findByPayUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.withdraw(99L, 1000))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("계좌 없음");
    }

    @Test
    @DisplayName("withdraw - 잔액과 동일한 금액 출금 가능 (경계값)")
    void withdraw_exactBalance_succeeds() {
        account.setBalance(5000);
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenReturn(new PayTransaction());

        PayAccount result = walletService.withdraw(100L, 5000);

        assertThat(result.getBalance()).isEqualTo(0);
    }
}