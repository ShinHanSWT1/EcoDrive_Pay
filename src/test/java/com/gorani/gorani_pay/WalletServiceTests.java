package com.gorani.gorani_pay;

import com.gorani.gorani_pay.dto.CreateAccountRequest;
import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayUser;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import com.gorani.gorani_pay.repository.PayLedgerRepository;
import com.gorani.gorani_pay.repository.PayTransactionRepository;
import com.gorani.gorani_pay.repository.PayUserRepository;
import com.gorani.gorani_pay.service.LedgerService;
import com.gorani.gorani_pay.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 지갑 서비스 테스트 정의함
@ExtendWith(MockitoExtension.class)
class WalletServiceTests {

    // 계좌 저장소 모킹함
    @Mock
    private PayAccountRepository accountRepository;
    // 거래 저장소 모킹함
    @Mock
    private PayTransactionRepository transactionRepository;
    // 원장 저장소 모킹함
    @Mock
    private PayLedgerRepository ledgerRepository;
    // 원장 서비스 모킹함
    @Mock
    private LedgerService ledgerService;
    // 결제 사용자 저장소 모킹함
    @Mock
    private PayUserRepository payUserRepository;

    // 지갑 서비스 테스트 대상 생성함
    @InjectMocks
    private WalletService walletService;

    // 기존 계좌 재사용 검증함
    @Test
    void createAccountShouldReturnExistingAccountWhenAccountAlreadyExists() {
        CreateAccountRequest request = request();
        PayUser payUser = new PayUser();
        payUser.setId(10L);
        PayAccount existingAccount = new PayAccount();
        existingAccount.setId(20L);
        existingAccount.setPayUserId(10L);

        when(payUserRepository.findByExternalUserId(1001L)).thenReturn(Optional.of(payUser));
        when(accountRepository.findByPayUserId(10L)).thenReturn(Optional.of(existingAccount));

        PayAccount result = walletService.createAccount(request);

        assertThat(result.getId()).isEqualTo(20L);
        verify(accountRepository, never()).save(any(PayAccount.class));
    }

    // 신규 계좌 생성 검증함
    @Test
    void createAccountShouldCreatePayUserAndWalletWhenNotExists() {
        CreateAccountRequest request = request();

        PayUser savedUser = new PayUser();
        savedUser.setId(30L);
        savedUser.setExternalUserId(1001L);
        savedUser.setUserName("테스터");
        savedUser.setEmail("tester@gorani.com");

        when(payUserRepository.findByExternalUserId(1001L)).thenReturn(Optional.empty());
        when(payUserRepository.save(any(PayUser.class))).thenReturn(savedUser);
        when(accountRepository.findByPayUserId(30L)).thenReturn(Optional.empty());
        when(accountRepository.save(any(PayAccount.class))).thenAnswer(invocation -> {
            PayAccount account = invocation.getArgument(0);
            account.setId(40L);
            return account;
        });

        PayAccount result = walletService.createAccount(request);

        assertThat(result.getId()).isEqualTo(40L);
        assertThat(result.getPayUserId()).isEqualTo(30L);
        assertThat(result.getOwnerName()).isEqualTo("홍길동");
        assertThat(result.getAccountNumber()).isNotBlank();

        ArgumentCaptor<PayUser> payUserCaptor = ArgumentCaptor.forClass(PayUser.class);
        verify(payUserRepository).save(payUserCaptor.capture());
        assertThat(payUserCaptor.getValue().getExternalUserId()).isEqualTo(1001L);
    }

    // 테스트 요청 본문 생성함
    private CreateAccountRequest request() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setExternalUserId(1001L);
        request.setUserName("테스터");
        request.setEmail("tester@gorani.com");
        request.setOwnerName("홍길동");
        request.setBankCode("088");
        request.setAccountNumber(null);
        return request;
    }
}
