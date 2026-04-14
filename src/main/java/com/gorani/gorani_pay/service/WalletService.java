package com.gorani.gorani_pay.service;

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

    public PayAccount getAccount(Long payUserId) {
        PayAccount account = accountRepository.findByPayUserId(payUserId)
                .orElseGet(() -> {
                    PayAccount newAccount = new PayAccount();
                    newAccount.setPayUserId(payUserId);
                    newAccount.setBalance(0);
                    newAccount.setPoints(0L);

                    // DB에 저장하고 바로 반환
                    return accountRepository.save(newAccount);
                });

        // 이후 로직은 동일
        Long monthlyUsage = getMonthlyUsage(account.getId());
        account.setMonthUsage(monthlyUsage);
        return account;
    // 계좌 생성 기능
    public PayAccount createAccount(CreateAccountRequest request) {
        PayUser payUser = payUserRepository.findByExternalUserId(request.getExternalUserId())
                .orElseGet(() -> createPayUser(request));

        PayAccount existing = accountRepository.findByPayUserId(payUser.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }

        PayAccount account = new PayAccount();
        account.setPayUserId(payUser.getId());
        account.setOwnerName(request.getOwnerName().trim());
        account.setBankCode(normalizeBlankToNull(request.getBankCode()));
        account.setAccountNumber(resolveAccountNumber(request.getAccountNumber()));
        account.setBalance(0);
        account.setStatus("ACTIVE");
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        return accountRepository.save(account);
    }

    // 계좌 조회 기능
    public PayAccount getAccount(Long userId) {
        return accountRepository.findByPayUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    // 외부 사용자 식별자 기반 계좌 조회 기능
    public PayAccount getAccountByExternalUserId(Long externalUserId) {
        PayUser payUser = payUserRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pay user not found"));
        return getAccount(payUser.getId());
    }

    // 거래내역 조회 기능
    public List<PayTransaction> getTransactions(Long userId) {
        PayAccount account = getAccount(userId);
        return transactionRepository.findByPayAccountIdOrderByIdDesc(account.getId());
    }

    // 원장내역 조회 기능
    public List<PayLedger> getLedgerEntries(Long userId) {
        PayAccount account = getAccount(userId);
        return ledgerRepository.findByPayAccountIdOrderByIdDesc(account.getId());
    }

    // 충전 기능
    public PayAccount charge(Long userId, Integer amount) {
        PayAccount account = getAccount(userId);

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

        return account;
    }

    // 출금 기능
    public PayAccount withdraw(Long userId, Integer amount) {
        PayAccount account = getAccount(userId);

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

        return account;
    }

    public Long getMonthlyUsage(Long payAccountId) {
        LocalDateTime startOfMonth = java.time.YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = java.time.YearMonth.now().atEndOfMonth().atTime(java.time.LocalTime.MAX);

        return transactionRepository.sumUsageByPeriod(payAccountId, startOfMonth, endOfMonth);
    // 결제 사용자 생성 기능
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

    // 계좌번호 결정 기능
    private String resolveAccountNumber(String accountNumber) {
        String normalized = normalizeBlankToNull(accountNumber);
        if (normalized != null) {
            return normalized;
        }
        return "GP-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    // 공백 문자열 정규화 기능
    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
