package com.gorani.gorani_pay.service;

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
import com.gorani.gorani_pay.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final long PAYMENT_EXPIRATION_MINUTES = 30L;

    private final PayPaymentRepository paymentRepository;
    private final PayAccountRepository accountRepository;
    private final PayTransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final PayRefundRepository refundRepository;
    private final WebhookLogService webhookLogService;

    @Transactional
    public PayPayment createPayment(CreatePaymentRequest request, String idempotencyKey) {
        Optional<String> existing = idempotencyService.findByKey(idempotencyKey);
        if (existing.isPresent()) {
            return JsonUtil.fromJson(existing.get(), PayPayment.class);
        }

        Optional<PayPayment> existingPayment = paymentRepository.findByExternalOrderId(request.getExternalOrderId());
        if (existingPayment.isPresent()) {
            PayPayment payment = existingPayment.get();
            if (!payment.getPayUserId().equals(request.getPayUserId())
                    || !payment.getPayAccountId().equals(request.getPayAccountId())) {
                throw new ApiException(HttpStatus.CONFLICT, "Payment already exists with different owner");
            }
            return payment;
        }

        PayAccount account = accountRepository.findById(request.getPayAccountId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!account.getPayUserId().equals(request.getPayUserId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Account does not belong to pay user");
        }
        if (request.getPointAmount() + request.getCouponDiscountAmount() > request.getAmount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Discount amounts exceed payment amount");
        }

        PayPayment payment = new PayPayment();
        payment.setPayUserId(request.getPayUserId());
        payment.setPayAccountId(request.getPayAccountId());
        payment.setExternalOrderId(request.getExternalOrderId());
        payment.setPaymentType(request.getPaymentType());
        payment.setPayProductId(request.getPayProductId());
        payment.setTitle(request.getTitle());
        payment.setAmount(request.getAmount());
        payment.setPointAmount(request.getPointAmount());
        payment.setCouponDiscountAmount(request.getCouponDiscountAmount());
        payment.setStatus("READY");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        PayPayment saved = paymentRepository.save(payment);
        idempotencyService.save(idempotencyKey, saved.getExternalOrderId(), JsonUtil.toJson(saved), "READY");
        return saved;
    }

    // ?붿븸 遺議?ApiException) 諛쒖깮 ???곸쐞 checkout ?쒕퉬???먮룞異⑹쟾 ?ъ떆???붽뎄?ы빆
    // ?몃옖??뀡 rollback-only 留덊궧 諛⑹? 紐⑹쟻 noRollbackFor ?곸슜
    @Transactional(noRollbackFor = ApiException.class)
    public PayPayment completePayment(Long paymentId, String idempotencyKey) {
        Optional<String> existing = idempotencyService.findByKey(idempotencyKey);
        if (existing.isPresent()) {
            return JsonUtil.fromJson(existing.get(), PayPayment.class);
        }

        PayPayment payment = getPayment(paymentId);
        if (!"READY".equals(payment.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment is not in READY status");
        }
        if (isExpired(payment)) {
            payment.setStatus("EXPIRED");
            payment.setUpdatedAt(LocalDateTime.now());
            webhookLogService.record("PAYMENT_EXPIRED", payment.getExternalOrderId(), payment);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment has expired");
        }

        PayAccount account = accountRepository.findByPayUserId(payment.getPayUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));

        // ?ъ씤??荑좏룿 ?좎씤 ?곸슜 ???ㅼ젣 吏媛?李④컧 湲덉븸 怨꾩궛 濡쒖쭅
        int pointAmount = payment.getPointAmount() == null ? 0 : payment.getPointAmount();
        int payableAmount = getPayableAmount(payment, pointAmount, account);

        PayTransaction tx = null;
        if (payableAmount > 0) {
            tx = new PayTransaction();
            tx.setPayAccountId(account.getId());
            tx.setPayPaymentId(payment.getId());
            tx.setTransactionType("PAYMENT");
            tx.setDirection("DEBIT");
            tx.setAmount(payableAmount);
            // ?몃? 二쇰Ц踰덊샇 prefix 湲곕컲 寃곗젣 ?좏삎 ?쒓퉭 泥섎━
            tx.setCategory(resolvePaymentCategory(payment));
            tx.setOccurredAt(LocalDateTime.now());
            transactionRepository.save(tx);
        }

        if (pointAmount > 0) {
            // ?ъ씤???ъ슜 ?댁뿭 蹂꾨룄 ?몃옖??뀡 湲곕줉 泥섎━
            PayTransaction pointTx = new PayTransaction();
            pointTx.setPayAccountId(account.getId());
            pointTx.setPayPaymentId(payment.getId());
            pointTx.setTransactionType("POINT_USE");
            pointTx.setDirection("DEBIT");
            pointTx.setAmount(pointAmount);
            pointTx.setCategory("POINT");
            pointTx.setOccurredAt(LocalDateTime.now());
            transactionRepository.save(pointTx);
            account.deductPoints((long) pointAmount);
        }

        if (payableAmount > 0 && tx != null) {
            account.deductBalance(payableAmount);
            ledgerService.record(
                    tx.getId(),
                    account.getId(),
                    "DEBIT",
                    payableAmount,
                    account.getBalance(),
                    "PAYMENT",
                    payment.getId()
            );
        }

        payment.setStatus("COMPLETED");
        payment.setApprovedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        idempotencyService.save(idempotencyKey, payment.getExternalOrderId(), JsonUtil.toJson(payment), "COMPLETED");
        webhookLogService.record("PAYMENT_COMPLETED", payment.getExternalOrderId(), payment);
        return payment;
    }

    private static int getPayableAmount(PayPayment payment, int pointAmount, PayAccount account) {
        int couponDiscountAmount = payment.getCouponDiscountAmount() == null ? 0 : payment.getCouponDiscountAmount();
        int payableAmount = payment.getAmount() - pointAmount - couponDiscountAmount;
        if (payableAmount < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payable amount cannot be negative");
        }

        if (pointAmount > 0 && account.getPoints() < pointAmount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient points");
        }
        if (account.getBalance() < payableAmount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }
        return payableAmount;
    }

    @Transactional
    public PayPayment cancelPayment(Long paymentId) {
        PayPayment payment = getPayment(paymentId);
        if ("COMPLETED".equals(payment.getStatus()) || "PARTIAL_REFUNDED".equals(payment.getStatus()) || "REFUNDED".equals(payment.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Processed payment cannot be canceled");
        }

        payment.setStatus("CANCELED");
        payment.setUpdatedAt(LocalDateTime.now());
        webhookLogService.record("PAYMENT_CANCELED", payment.getExternalOrderId(), payment);
        return payment;
    }

    @Transactional
    public PayPayment refund(Long paymentId, RefundRequest request, String idempotencyKey) {
        Optional<String> existing = idempotencyService.findByKey(idempotencyKey);
        if (existing.isPresent()) {
            return JsonUtil.fromJson(existing.get(), PayPayment.class);
        }

        PayPayment payment = getPayment(paymentId);
        if (!"COMPLETED".equals(payment.getStatus()) && !"PARTIAL_REFUNDED".equals(payment.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only completed payments can be refunded");
        }

        int refundedAmount = getRefundedAmount(payment.getId());
        int refundableAmount = payment.getAmount() - refundedAmount;
        if (request.getRefundAmount() > refundableAmount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Refund amount exceeds refundable amount");
        }

        PayAccount account = accountRepository.findByPayUserId(payment.getPayUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));

        PayTransaction tx = new PayTransaction();
        tx.setPayAccountId(account.getId());
        tx.setPayPaymentId(payment.getId());
        tx.setTransactionType("REFUND");
        tx.setDirection("CREDIT");
        tx.setAmount(request.getRefundAmount());
        tx.setOccurredAt(LocalDateTime.now());
        transactionRepository.save(tx);

        account.addBalance(request.getRefundAmount());
        ledgerService.record(
                tx.getId(),
                account.getId(),
                "CREDIT",
                request.getRefundAmount(),
                account.getBalance(),
                "REFUND",
                payment.getId()
        );

        PayRefund refund = new PayRefund();
        refund.setPayPaymentId(payment.getId());
        refund.setRefundAmount(request.getRefundAmount());
        refund.setReason(request.getReason());
        refund.setStatus("COMPLETED");
        refund.setRefundedAt(LocalDateTime.now());
        refundRepository.save(refund);

        int updatedRefundedAmount = refundedAmount + request.getRefundAmount();
        payment.setStatus(updatedRefundedAmount == payment.getAmount() ? "REFUNDED" : "PARTIAL_REFUNDED");
        payment.setUpdatedAt(LocalDateTime.now());

        idempotencyService.save(idempotencyKey, payment.getExternalOrderId(), JsonUtil.toJson(payment), payment.getStatus());
        webhookLogService.record("PAYMENT_REFUNDED", payment.getExternalOrderId(), Map.of(
                "paymentId", payment.getId(),
                "refundId", refund.getId(),
                "refundAmount", refund.getRefundAmount(),
                "status", payment.getStatus()
        ));
        return payment;
    }

    @Transactional(readOnly = true)
    public PayPayment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    @Transactional(readOnly = true)
    public List<PayPayment> getPayments(Long payUserId) {
        return paymentRepository.findByPayUserIdOrderByIdDesc(payUserId);
    }

    @Transactional(readOnly = true)
    public List<PayRefund> getRefunds(Long paymentId) {
        getPayment(paymentId);
        return refundRepository.findByPayPaymentIdOrderByIdDesc(paymentId);
    }

    @Transactional
    public List<PayPayment> expireReadyPayments(int olderThanMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(olderThanMinutes);
        List<PayPayment> readyPayments = paymentRepository.findByStatusAndCreatedAtBefore("READY", cutoff);

        for (PayPayment payment : readyPayments) {
            payment.setStatus("EXPIRED");
            payment.setUpdatedAt(LocalDateTime.now());
            webhookLogService.record("PAYMENT_EXPIRED", payment.getExternalOrderId(), payment);
        }

        return readyPayments;
    }

    private int getRefundedAmount(Long paymentId) {
        return refundRepository.findByPayPaymentIdOrderByIdDesc(paymentId).stream()
                .mapToInt(PayRefund::getRefundAmount)
                .sum();
    }

    private boolean isExpired(PayPayment payment) {
        return payment.getCreatedAt() != null
                && payment.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(PAYMENT_EXPIRATION_MINUTES));
    }

    private String resolvePaymentCategory(PayPayment payment) {
        String externalOrderId = payment.getExternalOrderId();
        if (externalOrderId != null && externalOrderId.startsWith("ECO-COUPON-")) {
            return "COUPON";
        }
        return "GENERAL";
    }
}

