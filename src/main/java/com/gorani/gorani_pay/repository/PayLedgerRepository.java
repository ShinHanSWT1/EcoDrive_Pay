package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayLedgerRepository extends JpaRepository<PayLedger, Long> {

    List<PayLedger> findByPayAccountIdOrderByIdDesc(Long payAccountId);
}