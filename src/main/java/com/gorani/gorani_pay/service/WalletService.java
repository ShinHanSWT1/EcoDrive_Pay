package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.client.TossPaymentClient;
import com.gorani.gorani_pay.dto.ChargeConfirmRequest;
import com.gorani.gorani_pay.dto.CreateAccountRequest;
import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayLedger;
import com.gorani.gorani_pay.entity.PayTransaction;
import com.gorani.gorani_pay.entity.PayUser;
import com.gorani.gorani_pay.exception.ApiException;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import com.gorani.gorani_pay.repository.PayLedgerRepository;
import com.gorani.gorani_pay.repository.PayTransactionRepository;
import com.gorani.gorani_pay.repository.PayUserRepository;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// 지갑 도메인 서비스
@Service
@RequiredArgsConstructor
@Transactional
public class WalletService {

    // 계좌 저장소
    private final PayAccountRepository accountRepository;
    // 거래 저장소
    private final PayTransactionRepository transactionRepository;
    // 원장 저장소
    private final PayLedgerRepository ledgerRepository;
    // 원장 기록 서비스
    private final LedgerService ledgerService;
    // 결제 사용자 저장소
    private final PayUserRepository payUserRepository;
    // Toss PG 연동
    private final TossPaymentClient tossPaymentClient;

    // 계좌 생성
    public PayAccount createAccount(CreateAccountRequest request) {
        PayUser payUser = payUserRepository.findByExternalUserId(request.getExternalUserId())
                .orElseGet(() -> createPayUser(request));

        PayAccount existing = accountRepository.findByPayUserId(payUser.getId()).orElse(null);
        if (existing != null) {
            existing.setMonthUsage(getMonthlyUsage(existing.getId()));
            return existing;
        }

        PayAccount account = new PayAccount();
        account.setPayUserId(payUser.getId());
        account.setOwnerName(request.getOwnerName().trim());
        account.setBankCode(normalizeBlankToNull(request.getBankCode()));
        account.setAccountNumber(resolveAccountNumber(request.getAccountNumber()));
        account.setBalance(0);
        account.setPoints(0L);
        account.setStatus("ACTIVE");
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        PayAccount saved = accountRepository.save(account);
        saved.setMonthUsage(0L);
        return saved;
    }

    // 계좌 조회
    public PayAccount getAccount(Long payUserId) {
        PayAccount account = findAccountOrThrow(payUserId);
        account.setMonthUsage(getMonthlyUsage(account.getId()));
        return account;
    }

    // 외부 사용자 연계키 기반 계좌 조회
    public PayAccount getAccountByExternalUserId(Long externalUserId) {
        PayUser payUser = payUserRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pay user not found"));
        return getAccount(payUser.getId());
    }

    // 거래내역 조회
    public List<PayTransaction> getTransactions(Long payUserId) {
        PayAccount account = findAccountOrThrow(payUserId);
        return transactionRepository.findByPayAccountIdOrderByIdDesc(account.getId());
    }

    // 원장내역 조회
    public List<PayLedger> getLedgerEntries(Long payUserId) {
        PayAccount account = findAccountOrThrow(payUserId);
        return ledgerRepository.findByPayAccountIdOrderByIdDesc(account.getId());
    }

    // 충전
    public PayAccount charge(Long payUserId, Integer amount) {
        PayAccount account = findAccountOrThrow(payUserId);

        try {
            account.addBalance(amount);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        PayTransaction tx = new PayTransaction();
        tx.setPayAccountId(account.getId());
        tx.setTransactionType("CHARGE");
        tx.setDirection("CREDIT");
        tx.setAmount(amount);
        tx.setOccurredAt(LocalDateTime.now());
        transactionRepository.save(tx);

        ledgerService.record(
                tx.getId(),
                account.getId(),
                "CREDIT",
                amount,
                account.getBalance(),
                "CHARGE",
                account.getId()
        );

        account.setMonthUsage(getMonthlyUsage(account.getId()));
        return account;
    }

    // 출금
    public PayAccount withdraw(Long payUserId, Integer amount) {
        PayAccount account = findAccountOrThrow(payUserId);

        try {
            account.deductBalance(amount);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        PayTransaction tx = new PayTransaction();
        tx.setPayAccountId(account.getId());
        tx.setTransactionType("WITHDRAW");
        tx.setDirection("DEBIT");
        tx.setAmount(amount);
        tx.setOccurredAt(LocalDateTime.now());
        transactionRepository.save(tx);

        ledgerService.record(
                tx.getId(),
                account.getId(),
                "DEBIT",
                amount,
                account.getBalance(),
                "WITHDRAW",
                account.getId()
        );

        account.setMonthUsage(getMonthlyUsage(account.getId()));
        return account;
    }

    // 월 사용액 조회 (PAYMENT 거래만 집계)
    public Long getMonthlyUsage(Long payAccountId) {
        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = YearMonth.now().atEndOfMonth().atTime(LocalTime.MAX);

        Long usage = transactionRepository.sumUsageByPeriod(payAccountId, startOfMonth, endOfMonth);
        return usage == null ? 0L : usage;
    }

    // 계좌 조회 공통
    private PayAccount findAccountOrThrow(Long payUserId) {
        return accountRepository.findByPayUserId(payUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    // 결제 사용자 생성
    private PayUser createPayUser(CreateAccountRequest request) {
        PayUser payUser = new PayUser();
        payUser.setExternalUserId(request.getExternalUserId());
        payUser.setUserName(request.getUserName().trim());
        payUser.setEmail(request.getEmail().trim());
        payUser.setStatus("ACTIVE");
        payUser.setCreatedAt(LocalDateTime.now());
        payUser.setUpdatedAt(LocalDateTime.now());
        return payUserRepository.save(payUser);
    }

    // 계좌번호 결정
    private String resolveAccountNumber(String accountNumber) {
        String normalized = normalizeBlankToNull(accountNumber);
        if (normalized != null) {
            return normalized;
        }
        return "GP-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    // 공백 문자열 정리
    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public PayAccount confirmCharge(ChargeConfirmRequest request) {
        // 1. 토스 서버 승인 요청 (트랜잭션)
        tossPaymentClient.confirmPayment(request.paymentKey(), request.orderId(), request.amount());

        // 2. 유저 지갑 조회 (기존 방식인 findAccountOrThrow 활용)
        PayAccount account = findAccountOrThrow(request.payUserId());

        // 3. 지갑 잔액 찐으로 증가!
        try {
            account.addBalance(request.amount());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        // 4. 이용 내역(영수증) 작성 (기존 코드 스타일과 완벽 통일)
        PayTransaction tx = new PayTransaction();
        tx.setPayAccountId(account.getId()); // 객체 대신 ID 값 세팅!
        tx.setTransactionType("CHARGE");     // 충전 타입
        tx.setDirection("CREDIT");           // 입금
        tx.setAmount(request.amount());
        tx.setOccurredAt(LocalDateTime.now());
        transactionRepository.save(tx);

        // 5. 기존 시스템처럼 원장(Ledger) 장부에도 기록 추가!
        ledgerService.record(
                tx.getId(),
                account.getId(),
                "CREDIT",
                request.amount(),
                account.getBalance(),
                "TOSS_CHARGE", // 토스 충전임을 명시
                account.getId()
        );

        return account;
    }
}
