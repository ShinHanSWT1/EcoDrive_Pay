package com.gorani.gorani_pay.dto;

import jakarta.validation.constraints.NotNull;

public record ChargeConfirmRequest(
        @NotNull Long payUserId,
        @NotNull String paymentKey, // 토스가 발급한 결제 키
        @NotNull String orderId,    // 우리가(프론트에서) 만든 주문 번호
        @NotNull Integer amount     // 충전 요청 금액
) {
}