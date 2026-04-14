package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PayTransactionRepository extends JpaRepository<PayTransaction, Long> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PayTransaction t " +
            "WHERE t.payAccountId = :payAccountId " +
            "AND t.transactionType = 'PAYMENT' " +
            "AND t.createdAt BETWEEN :startDate AND :endDate")
    Long sumUsageByPeriod(@Param("payAccountId") Long payAccountId,
                          @Param("startDate") LocalDateTime startDate,
                          @Param("endDate") LocalDateTime endDate);

    List<PayTransaction> findByPayAccountIdOrderByIdDesc(Long payAccountId);
}
