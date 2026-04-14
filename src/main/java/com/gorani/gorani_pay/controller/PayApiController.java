package com.gorani.gorani_pay.controller;

import com.gorani.gorani_pay.dto.AmountRequest;
import com.gorani.gorani_pay.dto.CreateAccountRequest;
import com.gorani.gorani_pay.dto.CreatePaymentRequest;
import com.gorani.gorani_pay.dto.ExpirePaymentsRequest;
import com.gorani.gorani_pay.dto.RefundRequest;
import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayLedger;
import com.gorani.gorani_pay.entity.PayPayment;
import com.gorani.gorani_pay.entity.PayRefund;
import com.gorani.gorani_pay.entity.PayTransaction;
import com.gorani.gorani_pay.entity.PayWebhookLog;
import com.gorani.gorani_pay.service.PaymentService;
import com.gorani.gorani_pay.service.WalletService;
import com.gorani.gorani_pay.service.WebhookLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayApiController {

    private final WalletService walletService;
    private final PaymentService paymentService;
    private final WebhookLogService webhookLogService;

    @PostMapping("/accounts")
    public PayAccount createAccount(@Valid @RequestBody CreateAccountRequest request) {
        log.info("[Pay] create account - externalUserId={}, ownerName={}", request.getExternalUserId(), request.getOwnerName());
        return walletService.createAccount(request);
    }

    @PostMapping("/charge")
    public PayAccount charge(@Valid @RequestBody AmountRequest request) {
        log.info("[Pay] charge request - payUserId={}, amount={}", request.getPayUserId(), request.getAmount());
        return walletService.charge(request.getPayUserId(), request.getAmount());
    }

    @PostMapping("/withdraw")
    public PayAccount withdraw(@Valid @RequestBody AmountRequest request) {
        log.info("[Pay] withdraw request - payUserId={}, amount={}", request.getPayUserId(), request.getAmount());
        return walletService.withdraw(request.getPayUserId(), request.getAmount());
    }

    @GetMapping("/account/{payUserId}")
    public PayAccount getAccount(@PathVariable Long payUserId) {
        log.info("[Pay] account lookup - payUserId={}", payUserId);
        return walletService.getAccount(payUserId);
    }

    @GetMapping("/accounts/by-external-user/{externalUserId}")
    public PayAccount getAccountByExternalUserId(@PathVariable Long externalUserId) {
        log.info("[Pay] account lookup by external user - externalUserId={}", externalUserId);
        return walletService.getAccountByExternalUserId(externalUserId);
    }

    @GetMapping("/account/{payUserId}/transactions")
    public List<PayTransaction> getTransactions(@PathVariable Long payUserId) {
        log.info("[Pay] transaction lookup - payUserId={}", payUserId);
        return walletService.getTransactions(payUserId);
    }

    @GetMapping("/account/{payUserId}/ledger")
    public List<PayLedger> getLedger(@PathVariable Long payUserId) {
        log.info("[Pay] ledger lookup - payUserId={}", payUserId);
        return walletService.getLedgerEntries(payUserId);
    }

    @PostMapping("/payments")
    public PayPayment createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        log.info("[Pay] create payment - key={}, payUserId={}, amount={}", idempotencyKey, request.getPayUserId(), request.getAmount());
        return paymentService.createPayment(request, idempotencyKey);
    }

    @GetMapping("/payments/{paymentId}")
    public PayPayment getPayment(@PathVariable Long paymentId) {
        log.info("[Pay] payment lookup - paymentId={}", paymentId);
        return paymentService.getPayment(paymentId);
    }

    @GetMapping("/payments")
    public List<PayPayment> getPayments(@RequestParam Long payUserId) {
        log.info("[Pay] payment list - payUserId={}", payUserId);
        return paymentService.getPayments(payUserId);
    }

    @PostMapping("/payments/{paymentId}/complete")
    public PayPayment completePayment(
            @RequestHeader("Idempotency-Key") String key,
            @PathVariable Long paymentId
    ) {
        log.info("[Pay] complete payment - key={}, paymentId={}", key, paymentId);
        return paymentService.completePayment(paymentId, key);
    }

    @PostMapping("/payments/{paymentId}/cancel")
    public PayPayment cancelPayment(@PathVariable Long paymentId) {
        log.info("[Pay] cancel payment - paymentId={}", paymentId);
        return paymentService.cancelPayment(paymentId);
    }

    @GetMapping("/payments/{paymentId}/refunds")
    public List<PayRefund> getRefunds(@PathVariable Long paymentId) {
        log.info("[Pay] refund list - paymentId={}", paymentId);
        return paymentService.getRefunds(paymentId);
    }

    @PostMapping("/payments/{paymentId}/refund")
    public PayPayment refund(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RefundRequest request,
            @PathVariable Long paymentId
    ) {
        log.info("[Pay] refund request - key={}, paymentId={}, amount={}", key, paymentId, request.getRefundAmount());
        return paymentService.refund(paymentId, request, key);
    }

    @PostMapping("/payments/expire-ready")
    public List<PayPayment> expireReadyPayments(@Valid @RequestBody ExpirePaymentsRequest request) {
        log.info("[Pay] expire ready payments - olderThanMinutes={}", request.getOlderThanMinutes());
        return paymentService.expireReadyPayments(request.getOlderThanMinutes());
    }

    @GetMapping("/webhooks")
    public List<PayWebhookLog> getWebhookLogs(@RequestParam(required = false) String externalOrderId) {
        log.info("[Pay] webhook logs - externalOrderId={}", externalOrderId);
        return webhookLogService.getLogs(externalOrderId);
    }
}
