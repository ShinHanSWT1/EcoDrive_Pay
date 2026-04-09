package com.gorani.gorani_pay;

import com.gorani.gorani_pay.dto.CreatePaymentRequest;
import com.gorani.gorani_pay.dto.RefundRequest;
import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayPayment;
import com.gorani.gorani_pay.entity.PayRefund;
import com.gorani.gorani_pay.entity.PayTransaction;
import com.gorani.gorani_pay.exception.ApiException;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import com.gorani.gorani_pay.repository.PayPaymentRepository;
import com.gorani.gorani_pay.repository.PayRefundRepository;
import com.gorani.gorani_pay.repository.PayTransactionRepository;
import com.gorani.gorani_pay.service.IdempotencyService;
import com.gorani.gorani_pay.service.LedgerService;
import com.gorani.gorani_pay.service.PaymentService;
import com.gorani.gorani_pay.service.WebhookLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PayRefundRepository refundRepository;

    @Mock
    private WebhookLogService webhookLogService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void completePaymentShouldBeIdempotent() {
        PayPayment payment = payment(1L, 1L, 5000, "READY");
        PayAccount account = account(1L, 1L, 10000);

        when(idempotencyService.findByKey("complete-key")).thenReturn(Optional.empty());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(accountRepository.findByPayUserId(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(PayTransaction.class))).thenAnswer(invocation -> {
            PayTransaction tx = invocation.getArgument(0);
            tx.setId(10L);
            return tx;
        });

        PayPayment completed = paymentService.completePayment(1L, "complete-key");

        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(account.getBalance()).isEqualTo(5000);
        verify(idempotencyService).save(eq("complete-key"), eq("ORDER-1"), any(String.class), eq("COMPLETED"));
        verify(ledgerService).record(10L, 1L, "DEBIT", 5000, 5000, "PAYMENT", 1L);
    }

    @Test
    void refundShouldSupportPartialRefundWithoutSchemaChange() {
        PayPayment payment = payment(1L, 1L, 10000, "COMPLETED");
        PayAccount account = account(1L, 1L, 3000);
        RefundRequest request = new RefundRequest();
        request.setRefundAmount(4000);
        request.setReason("partial cancel");

        when(idempotencyService.findByKey("refund-key")).thenReturn(Optional.empty());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(accountRepository.findByPayUserId(1L)).thenReturn(Optional.of(account));
        when(refundRepository.findByPayPaymentIdOrderByIdDesc(1L)).thenReturn(List.of());
        when(transactionRepository.save(any(PayTransaction.class))).thenAnswer(invocation -> {
            PayTransaction tx = invocation.getArgument(0);
            tx.setId(20L);
            return tx;
        });
        when(refundRepository.save(any(PayRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayPayment refunded = paymentService.refund(1L, request, "refund-key");

        assertThat(refunded.getStatus()).isEqualTo("PARTIAL_REFUNDED");
        assertThat(account.getBalance()).isEqualTo(7000);
        verify(idempotencyService).save(eq("refund-key"), eq("ORDER-1"), any(String.class), eq("PARTIAL_REFUNDED"));
    }

    @Test
    void expireReadyPaymentsShouldMarkOldPaymentsExpired() {
        PayPayment payment = payment(1L, 1L, 1000, "READY");
        payment.setCreatedAt(LocalDateTime.now().minusMinutes(40));

        when(paymentRepository.findByStatusAndCreatedAtBefore(eq("READY"), any(LocalDateTime.class)))
                .thenReturn(List.of(payment));

        List<PayPayment> expired = paymentService.expireReadyPayments(30);

        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getStatus()).isEqualTo("EXPIRED");
        verify(webhookLogService).record("PAYMENT_EXPIRED", "ORDER-1", payment);
    }

    @Test
    void createPaymentShouldValidateDiscountAmount() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPayUserId(1L);
        request.setPayAccountId(1L);
        request.setExternalOrderId("ORDER-1");
        request.setPaymentType("WALLET");
        request.setTitle("Charge");
        request.setAmount(1000);
        request.setPointAmount(800);
        request.setCouponDiscountAmount(300);

        when(idempotencyService.findByKey("create-key")).thenReturn(Optional.empty());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, 1L, 5000)));

        assertThatThrownBy(() -> paymentService.createPayment(request, "create-key"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Discount amounts exceed payment amount");

        verify(paymentRepository, never()).save(any(PayPayment.class));
    }

    private PayPayment payment(Long id, Long payUserId, int amount, String status) {
        PayPayment payment = new PayPayment();
        payment.setId(id);
        payment.setPayUserId(payUserId);
        payment.setPayAccountId(1L);
        payment.setExternalOrderId("ORDER-1");
        payment.setPaymentType("WALLET");
        payment.setTitle("Test payment");
        payment.setAmount(amount);
        payment.setStatus(status);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        return payment;
    }

    private PayAccount account(Long id, Long payUserId, int balance) {
        PayAccount account = new PayAccount();
        account.setId(id);
        account.setPayUserId(payUserId);
        account.setAccountNumber("100-200");
        account.setOwnerName("tester");
        account.setBalance(balance);
        return account;
    }
}
