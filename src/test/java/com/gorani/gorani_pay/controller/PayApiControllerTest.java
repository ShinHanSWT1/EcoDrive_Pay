package com.gorani.gorani_pay.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gorani.gorani_pay.config.SecurityConfig;
import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayPayment;
import com.gorani.gorani_pay.service.PaymentService;
import com.gorani.gorani_pay.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PayApiController.class)
@Import(SecurityConfig.class)
class PayApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private PaymentService paymentService;

    private PayAccount sampleAccount;
    private PayPayment samplePayment;

    @BeforeEach
    void setUp() {
        sampleAccount = new PayAccount();
        sampleAccount.setId(1L);
        sampleAccount.setPayUserId(100L);
        sampleAccount.setAccountNumber("111-222-333");
        sampleAccount.setOwnerName("테스트 유저");
        sampleAccount.setBalance(50000);
        sampleAccount.setStatus("ACTIVE");

        samplePayment = new PayPayment();
        samplePayment.setId(10L);
        samplePayment.setPayUserId(100L);
        samplePayment.setPayAccountId(1L);
        samplePayment.setExternalOrderId("ORDER-001");
        samplePayment.setTitle("테스트 상품");
        samplePayment.setAmount(10000);
        samplePayment.setStatus("READY");
        samplePayment.setPaymentType("WALLET");
    }

    // ── POST /api/v1/pay/charge ───────────────────────────────────

    @Test
    @DisplayName("POST /charge - 충전 요청 성공")
    void charge_success() throws Exception {
        when(walletService.charge(100L, 5000)).thenReturn(sampleAccount);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", 100);
        body.put("amount", 5000);

        mockMvc.perform(post("/api/v1/pay/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payUserId").value(100))
                .andExpect(jsonPath("$.balance").value(50000));

        verify(walletService).charge(100L, 5000);
    }

    @Test
    @DisplayName("POST /charge - 서비스 예외 시 500 응답")
    void charge_serviceThrows_returns500() throws Exception {
        when(walletService.charge(eq(999L), any())).thenThrow(new RuntimeException("계좌 없음"));

        Map<String, Object> body = new HashMap<>();
        body.put("userId", 999);
        body.put("amount", 1000);

        mockMvc.perform(post("/api/v1/pay/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is5xxServerError());
    }

    // ── POST /api/v1/pay/withdraw ─────────────────────────────────

    @Test
    @DisplayName("POST /withdraw - 출금 요청 성공")
    void withdraw_success() throws Exception {
        sampleAccount.setBalance(45000);
        when(walletService.withdraw(100L, 5000)).thenReturn(sampleAccount);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", 100);
        body.put("amount", 5000);

        mockMvc.perform(post("/api/v1/pay/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(45000));

        verify(walletService).withdraw(100L, 5000);
    }

    @Test
    @DisplayName("POST /withdraw - 잔액 부족 시 500 응답")
    void withdraw_insufficientBalance_returns500() throws Exception {
        when(walletService.withdraw(any(), any())).thenThrow(new RuntimeException("잔액 부족"));

        Map<String, Object> body = new HashMap<>();
        body.put("userId", 100);
        body.put("amount", 99999);

        mockMvc.perform(post("/api/v1/pay/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is5xxServerError());
    }

    // ── GET /api/v1/pay/account/{userId} ─────────────────────────

    @Test
    @DisplayName("GET /account/{userId} - 계좌 조회 성공")
    void getAccount_success() throws Exception {
        when(walletService.getAccount(100L)).thenReturn(sampleAccount);

        mockMvc.perform(get("/api/v1/pay/account/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payUserId").value(100))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(walletService).getAccount(100L);
    }

    @Test
    @DisplayName("GET /account/{userId} - 계좌 없음 시 500 응답")
    void getAccount_notFound_returns500() throws Exception {
        when(walletService.getAccount(999L)).thenThrow(new RuntimeException("계좌 없음"));

        mockMvc.perform(get("/api/v1/pay/account/999"))
                .andExpect(status().is5xxServerError());
    }

    // ── POST /api/v1/pay/payments ─────────────────────────────────

    @Test
    @DisplayName("POST /payments - 결제 생성 성공")
    void createPayment_success() throws Exception {
        when(paymentService.createPayment(any(PayPayment.class))).thenReturn(samplePayment);

        mockMvc.perform(post("/api/v1/pay/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePayment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.externalOrderId").value("ORDER-001"));

        verify(paymentService).createPayment(any(PayPayment.class));
    }

    // ── POST /api/v1/pay/payments/{paymentId}/complete ────────────

    @Test
    @DisplayName("POST /payments/{paymentId}/complete - 결제 완료 성공")
    void completePayment_success() throws Exception {
        samplePayment.setStatus("COMPLETED");
        when(paymentService.completePayment(10L)).thenReturn(samplePayment);

        mockMvc.perform(post("/api/v1/pay/payments/10/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(paymentService).completePayment(10L);
    }

    @Test
    @DisplayName("POST /payments/{paymentId}/complete - 이미 처리된 결제 시 500 응답")
    void completePayment_alreadyProcessed_returns500() throws Exception {
        when(paymentService.completePayment(10L)).thenThrow(new RuntimeException("이미 처리된 결제"));

        mockMvc.perform(post("/api/v1/pay/payments/10/complete"))
                .andExpect(status().is5xxServerError());
    }

    // ── POST /api/v1/pay/payments/{paymentId}/cancel ──────────────

    @Test
    @DisplayName("POST /payments/{paymentId}/cancel - 결제 취소 성공")
    void cancelPayment_success() throws Exception {
        samplePayment.setStatus("CANCELED");
        when(paymentService.cancelPayment(10L)).thenReturn(samplePayment);

        mockMvc.perform(post("/api/v1/pay/payments/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        verify(paymentService).cancelPayment(10L);
    }

    @Test
    @DisplayName("POST /payments/{paymentId}/cancel - 완료된 결제 취소 시 500 응답")
    void cancelPayment_completedPayment_returns500() throws Exception {
        when(paymentService.cancelPayment(10L)).thenThrow(new RuntimeException("완료된 결제 취소 불가"));

        mockMvc.perform(post("/api/v1/pay/payments/10/cancel"))
                .andExpect(status().is5xxServerError());
    }
}