package com.gorani.gorani_pay.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpirePaymentsRequest {

    @Min(1)
    private Integer olderThanMinutes = 30;
}
