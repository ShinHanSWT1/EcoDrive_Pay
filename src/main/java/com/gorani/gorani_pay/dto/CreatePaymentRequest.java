package com.gorani.gorani_pay.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePaymentRequest {

    @NotNull
    private Long payUserId;

    @NotNull
    private Long payAccountId;

    @NotBlank
    private String externalOrderId;

    @NotBlank
    private String paymentType;

    private Long payProductId;

    @NotBlank
    private String title;

    @NotNull
    @Min(1)
    private Integer amount;

    @NotNull
    @Min(0)
    private Integer pointAmount = 0;

    @NotNull
    @Min(0)
    private Integer couponDiscountAmount = 0;
}
