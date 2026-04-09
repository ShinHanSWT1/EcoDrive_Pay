package com.gorani.gorani_pay.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AmountRequest {

    @NotNull
    private Long payUserId;

    @NotNull
    @Min(1)
    private Integer amount;
}
