package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.entity.PayLedger;
import com.gorani.gorani_pay.repository.PayLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private PayLedgerRepository ledgerRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        when(ledgerRepository.save(any(PayLedger.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("record - 올바른 필드로 PayLedger를 저장한다")
    void record_savesLedgerWithCorrectFields() {
        ledgerService.record(10L, 1L, "CREDIT", 5000, 15000, "CHARGE", 1L);

        ArgumentCaptor<PayLedger> captor = ArgumentCaptor.forClass(PayLedger.class);
        verify(ledgerRepository).save(captor.capture());

        PayLedger saved = captor.getValue();
        assertThat(saved.getTransactionId()).isEqualTo(10L);
        assertThat(saved.getPayAccountId()).isEqualTo(1L);
        assertThat(saved.getType()).isEqualTo("CREDIT");
        assertThat(saved.getAmount()).isEqualTo(5000);
        assertThat(saved.getBalanceAfter()).isEqualTo(15000);
        assertThat(saved.getReferenceType()).isEqualTo("CHARGE");
        assertThat(saved.getReferenceId()).isEqualTo(1L);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("record - DEBIT 타입으로 저장한다")
    void record_debitType_savedCorrectly() {
        ledgerService.record(20L, 2L, "DEBIT", 3000, 7000, "PAYMENT", 99L);

        ArgumentCaptor<PayLedger> captor = ArgumentCaptor.forClass(PayLedger.class);
        verify(ledgerRepository).save(captor.capture());

        PayLedger saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo("DEBIT");
        assertThat(saved.getReferenceType()).isEqualTo("PAYMENT");
        assertThat(saved.getReferenceId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("record - repository.save 가 정확히 한 번 호출된다")
    void record_callsSaveExactlyOnce() {
        ledgerService.record(1L, 1L, "CREDIT", 1000, 2000, "CHARGE", 1L);
        verify(ledgerRepository, times(1)).save(any(PayLedger.class));
    }

    @Test
    @DisplayName("record - WITHDRAW 참조 타입으로 저장한다")
    void record_withdrawReferenceType_savedCorrectly() {
        ledgerService.record(30L, 3L, "DEBIT", 2000, 3000, "WITHDRAW", 3L);

        ArgumentCaptor<PayLedger> captor = ArgumentCaptor.forClass(PayLedger.class);
        verify(ledgerRepository).save(captor.capture());

        assertThat(captor.getValue().getReferenceType()).isEqualTo("WITHDRAW");
    }
}