package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayTransactionRepository extends JpaRepository<PayTransaction, Long> {

    List<PayTransaction> findByPayAccountIdOrderByIdDesc(Long payAccountId);
}
