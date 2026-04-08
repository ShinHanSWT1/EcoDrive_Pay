package com.gorani.gorani_pay.controller;

import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayPayment;
import com.gorani.gorani_pay.service.PaymentService;
import com.gorani.gorani_pay.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayApiController {

    private final WalletService walletService;
    private final PaymentService paymentService;

    // Wallet
    // 충전
    @PostMapping("/charge")
    public PayAccount charge(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        Integer amount = Integer.valueOf(request.get("amount").toString());
        log.info("[Pay] 충전 요청 - userId={}, amount={}", userId, amount);

        return walletService.charge(userId, amount);
    }

    // 출금
    @PostMapping("/withdraw")
    public PayAccount withdraw(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        Integer amount = Integer.valueOf(request.get("amount").toString());
        log.info("[Pay] 출금 요청 - userId={}, amount={}", userId, amount);

        return walletService.withdraw(userId, amount);
    }

    // 계좌 조회
    @GetMapping("/account/{userId}")
    public PayAccount getAccount(@PathVariable Long userId) {
        log.info("[Pay] 계좌 조회 - userId={}", userId);

        return walletService.getAccount(userId);
    }

    // Payment
    // 결제 생성
    @PostMapping("/payments")
    public PayPayment createPayment(@RequestBody PayPayment request) {
        log.info("[Pay] 결제 생성 - userId={}, amount={}, orderId={}",
                request.getPayUserId(),
                request.getAmount(),
                request.getExternalOrderId());

        return paymentService.createPayment(request);
    }

    // 결제 완료
    @PostMapping("/payments/{paymentId}/complete")
    public PayPayment completePayment(@PathVariable Long paymentId) {
        log.info("[Pay] 결제 완료 - paymentId={}", paymentId);

        return paymentService.completePayment(paymentId);
    }

    // 결제 취소
    @PostMapping("/payments/{paymentId}/cancel")
    public PayPayment cancelPayment(@PathVariable Long paymentId) {
        log.info("[Pay] 결제 취소 - paymentId={}", paymentId);

        return paymentService.cancelPayment(paymentId);
    }
}