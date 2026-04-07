package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayTransaction;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import com.gorani.gorani_pay.repository.PayTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final PayAccountRepository accountRepository;
    private final PayTransactionRepository transactionRepository;
    private final LedgerService ledgerService;

    public PayAccount getAccount(Long userId) {
        return accountRepository.findByPayUserId(userId)
                .orElseThrow(() -> new RuntimeException("계좌 없음"));
    }

    @Transactional
    public PayAccount charge(Long userId, Integer amount) {

        PayAccount account = getAccount(userId);

        account.addBalance(amount);

        // Transaction
        PayTransaction tx = new PayTransaction();
        tx.setPayAccountId(account.getId());
        tx.setTransactionType("CHARGE");
        tx.setDirection("CREDIT");
        tx.setAmount(amount);
        tx.setOccurredAt(LocalDateTime.now());

        transactionRepository.save(tx);

        // Ledger
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

    @Transactional
    public PayAccount withdraw(Long userId, Integer amount) {

        PayAccount account = getAccount(userId);

        account.deductBalance(amount);

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
}