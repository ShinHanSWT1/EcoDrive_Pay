package com.gorani.gorani_pay.dto;

public record CheckoutStatusSnapshot(
        String sessionToken,
        String status,
        String channel,
        String successRedirectUrl,
        String failRedirectUrl
) {
}
