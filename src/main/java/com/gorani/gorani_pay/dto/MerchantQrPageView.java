package com.gorani.gorani_pay.dto;

public record MerchantQrPageView(
        String sessionToken,
        String title,
        String status
) {
}
