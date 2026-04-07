package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.entity.*;
import com.gorani.gorani_pay.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PayPaymentRepository paymentRepository;
    private final PayAccountRepository accountRepository;
    private final PayTransactionRepository transactionRepository;
    private final LedgerService ledgerService;

    // 결제 생성
    public PayPayment createPayment(PayPayment request) {

        request.setStatus("READY");
        request.setCreatedAt(LocalDateTime.now());

        return paymentRepository.save(request);
    }

    // 결제 완료
    @Transactional
    public PayPayment completePayment(Long paymentId) {

        PayPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("결제 없음"));

        if (!payment.getStatus().equals("READY")) {
            throw new RuntimeException("이미 처리된 결제");
        }

        PayAccount account = accountRepository.findByPayUserId(payment.getPayUserId())
                .orElseThrow(() -> new RuntimeException("계좌 없음"));

        if (account.getBalance() < payment.getAmount()) {
            throw new RuntimeException("잔액 부족");
        }

        // Transaction 생성
        PayTransaction tx = new PayTransaction();
        tx.setPayAccountId(account.getId());
        tx.setPayPaymentId(payment.getId());
        tx.setTransactionType("PAYMENT");
        tx.setDirection("DEBIT");
        tx.setAmount(payment.getAmount());
        tx.setOccurredAt(LocalDateTime.now());

        transactionRepository.save(tx);

        // 잔액 차감
        account.deductBalance(payment.getAmount());

        // Ledger 기록
        ledgerService.record(
                tx.getId(),
                account.getId(),
                "DEBIT",
                payment.getAmount(),
                account.getBalance(),
                "PAYMENT",
                payment.getId()
        );

        // 상태 변경
        payment.setStatus("COMPLETED");
        payment.setApprovedAt(LocalDateTime.now());

        return payment;
    }

    // 결제 취소
    @Transactional
    public PayPayment cancelPayment(Long paymentId) {

        PayPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("결제 없음"));

        if (payment.getStatus().equals("COMPLETED")) {
            throw new RuntimeException("완료된 결제 취소 불가");
        }

        payment.setStatus("CANCELED");

        return payment;
    }
}