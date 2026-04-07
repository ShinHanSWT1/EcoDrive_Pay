package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.entity.PayLedger;
import com.gorani.gorani_pay.repository.PayLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final PayLedgerRepository ledgerRepository;

    public void record(
            Long transactionId,
            Long accountId,
            String type,
            Integer amount,
            Integer balanceAfter,
            String referenceType,
            Long referenceId
    ) {
        PayLedger ledger = new PayLedger();
        ledger.setTransactionId(transactionId);
        ledger.setPayAccountId(accountId);
        ledger.setType(type);
        ledger.setAmount(amount);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setReferenceType(referenceType);
        ledger.setReferenceId(referenceId);
        ledger.setCreatedAt(LocalDateTime.now());

        ledgerRepository.save(ledger);
    }
}