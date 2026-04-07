package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayPayment;
import com.gorani.gorani_pay.entity.PayTransaction;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import com.gorani.gorani_pay.repository.PayPaymentRepository;
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
class PaymentServiceTest {

    @Mock
    private PayPaymentRepository paymentRepository;

    @Mock
    private PayAccountRepository accountRepository;

    @Mock
    private PayTransactionRepository transactionRepository;

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private PaymentService paymentService;

    private PayPayment readyPayment;
    private PayAccount account;

    @BeforeEach
    void setUp() {
        account = new PayAccount();
        account.setId(1L);
        account.setPayUserId(100L);
        account.setBalance(50000);

        readyPayment = new PayPayment();
        readyPayment.setId(10L);
        readyPayment.setPayUserId(100L);
        readyPayment.setPayAccountId(1L);
        readyPayment.setExternalOrderId("ORDER-001");
        readyPayment.setTitle("테스트 상품");
        readyPayment.setAmount(10000);
        readyPayment.setStatus("READY");
        readyPayment.setPaymentType("WALLET");
    }

    // ── createPayment ─────────────────────────────────────────────

    @Test
    @DisplayName("createPayment - READY 상태로 저장된다")
    void createPayment_setsReadyStatus() {
        PayPayment request = new PayPayment();
        request.setStatus("PENDING");
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayPayment result = paymentService.createPayment(request);

        assertThat(result.getStatus()).isEqualTo("READY");
    }

    @Test
    @DisplayName("createPayment - createdAt 이 설정된다")
    void createPayment_setsCreatedAt() {
        PayPayment request = new PayPayment();
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayPayment result = paymentService.createPayment(request);

        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("createPayment - repository.save 가 호출된다")
    void createPayment_callsSave() {
        PayPayment request = new PayPayment();
        when(paymentRepository.save(any())).thenReturn(request);

        paymentService.createPayment(request);

        verify(paymentRepository, times(1)).save(request);
    }

    // ── completePayment ───────────────────────────────────────────

    @Test
    @DisplayName("completePayment - 성공 시 COMPLETED 상태로 변경된다")
    void completePayment_success_statusBecomesCompleted() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));
        PayTransaction savedTx = new PayTransaction();
        savedTx.setId(50L);
        when(transactionRepository.save(any())).thenReturn(savedTx);

        PayPayment result = paymentService.completePayment(10L);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("completePayment - 잔액이 결제금액만큼 차감된다")
    void completePayment_success_balanceDeducted() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenReturn(new PayTransaction());

        paymentService.completePayment(10L);

        assertThat(account.getBalance()).isEqualTo(40000);
    }

    @Test
    @DisplayName("completePayment - PAYMENT/DEBIT 트랜잭션이 저장된다")
    void completePayment_savesPaymentDebitTransaction() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenReturn(new PayTransaction());

        paymentService.completePayment(10L);

        ArgumentCaptor<PayTransaction> txCaptor = ArgumentCaptor.forClass(PayTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        PayTransaction tx = txCaptor.getValue();
        assertThat(tx.getTransactionType()).isEqualTo("PAYMENT");
        assertThat(tx.getDirection()).isEqualTo("DEBIT");
        assertThat(tx.getAmount()).isEqualTo(10000);
        assertThat(tx.getPayAccountId()).isEqualTo(1L);
        assertThat(tx.getPayPaymentId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("completePayment - LedgerService.record 가 DEBIT/PAYMENT 으로 호출된다")
    void completePayment_recordsLedgerDebitPayment() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));
        PayTransaction savedTx = new PayTransaction();
        savedTx.setId(50L);
        when(transactionRepository.save(any())).thenReturn(savedTx);

        paymentService.completePayment(10L);

        // balance after = 50000 - 10000 = 40000
        verify(ledgerService).record(
                eq(50L),
                eq(account.getId()),
                eq("DEBIT"),
                eq(10000),
                eq(40000),
                eq("PAYMENT"),
                eq(readyPayment.getId())
        );
    }

    @Test
    @DisplayName("completePayment - 결제가 없으면 RuntimeException 발생")
    void completePayment_paymentNotFound_throwsException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.completePayment(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("결제 없음");
    }

    @Test
    @DisplayName("completePayment - READY 상태가 아니면 RuntimeException 발생")
    void completePayment_notReady_throwsException() {
        readyPayment.setStatus("COMPLETED");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));

        assertThatThrownBy(() -> paymentService.completePayment(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("이미 처리된 결제");
    }

    @Test
    @DisplayName("completePayment - CANCELED 상태이면 RuntimeException 발생")
    void completePayment_canceledStatus_throwsException() {
        readyPayment.setStatus("CANCELED");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));

        assertThatThrownBy(() -> paymentService.completePayment(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("이미 처리된 결제");
    }

    @Test
    @DisplayName("completePayment - 계좌가 없으면 RuntimeException 발생")
    void completePayment_accountNotFound_throwsException() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.completePayment(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("계좌 없음");
    }

    @Test
    @DisplayName("completePayment - 잔액 부족 시 RuntimeException 발생")
    void completePayment_insufficientBalance_throwsException() {
        account.setBalance(5000);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> paymentService.completePayment(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("잔액 부족");

        verify(transactionRepository, never()).save(any());
        verify(ledgerService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("completePayment - 잔액과 결제금액이 동일할 때 성공 (경계값)")
    void completePayment_exactBalance_succeeds() {
        account.setBalance(10000);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));
        when(accountRepository.findByPayUserId(100L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenReturn(new PayTransaction());

        PayPayment result = paymentService.completePayment(10L);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(account.getBalance()).isEqualTo(0);
    }

    // ── cancelPayment ─────────────────────────────────────────────

    @Test
    @DisplayName("cancelPayment - READY 상태에서 취소 시 CANCELED 로 변경된다")
    void cancelPayment_readyPayment_becomesCancel() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));

        PayPayment result = paymentService.cancelPayment(10L);

        assertThat(result.getStatus()).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("cancelPayment - COMPLETED 상태에서 취소하면 RuntimeException 발생")
    void cancelPayment_completedPayment_throwsException() {
        readyPayment.setStatus("COMPLETED");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));

        assertThatThrownBy(() -> paymentService.cancelPayment(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("완료된 결제 취소 불가");
    }

    @Test
    @DisplayName("cancelPayment - 결제가 없으면 RuntimeException 발생")
    void cancelPayment_paymentNotFound_throwsException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.cancelPayment(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("결제 없음");
    }

    @Test
    @DisplayName("cancelPayment - 이미 CANCELED 상태에서 다시 취소 가능하다 (idempotent 허용)")
    void cancelPayment_alreadyCanceled_canCancelAgain() {
        readyPayment.setStatus("CANCELED");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(readyPayment));

        // cancelPayment only blocks COMPLETED; CANCELED can be re-canceled (no-op)
        PayPayment result = paymentService.cancelPayment(10L);
        assertThat(result.getStatus()).isEqualTo("CANCELED");
    }
}