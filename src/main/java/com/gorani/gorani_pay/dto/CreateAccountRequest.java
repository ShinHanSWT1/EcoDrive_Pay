package com.gorani.gorani_pay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// 계좌 생성 요청 정의
@Getter
@Setter
public class CreateAccountRequest {

    // 외부 사용자 식별자
    @NotNull
    private Long externalUserId;

    // 사용자 이름
    @NotBlank
    private String userName;

    // 사용자 이메일
    @NotBlank
    @Email
    private String email;

    // 계좌 소유자명
    @NotBlank
    private String ownerName;

    // 은행 코드
    private String bankCode;

    // 계좌번호
    private String accountNumber;
}
