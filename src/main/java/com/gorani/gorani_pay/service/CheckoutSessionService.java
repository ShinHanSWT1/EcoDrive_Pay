package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.dto.CheckoutPageView;
import com.gorani.gorani_pay.dto.CheckoutProcessResult;
import com.gorani.gorani_pay.dto.CheckoutSessionResponse;
import com.gorani.gorani_pay.dto.CheckoutStatusSnapshot;
import com.gorani.gorani_pay.dto.MerchantQrPageView;
import com.gorani.gorani_pay.dto.CreateCheckoutByCodeRequest;
import com.gorani.gorani_pay.dto.CreateCheckoutSessionRequest;
import com.gorani.gorani_pay.dto.CreatePaymentRequest;
import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayCheckoutChannel;
import com.gorani.gorani_pay.entity.PayCheckoutEntryMode;
import com.gorani.gorani_pay.entity.PayCheckoutIntegrationType;
import com.gorani.gorani_pay.entity.PayCheckoutSession;
import com.gorani.gorani_pay.entity.PayCheckoutSessionStatus;
import com.gorani.gorani_pay.entity.PayPayment;
import com.gorani.gorani_pay.exception.ApiException;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import com.gorani.gorani_pay.repository.PayCheckoutSessionRepository;
import com.gorani.gorani_pay.repository.PayUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutSessionService {

    private static final long SESSION_EXPIRE_MINUTES = 15L;
    private static final long CODE_TOKEN_EXPIRE_MINUTES = 3L;

    private final PayCheckoutSessionRepository payCheckoutSessionRepository;
    private final PayAccountRepository payAccountRepository;
    private final PayUserRepository payUserRepository;
    private final CheckoutMerchantPolicyService checkoutMerchantPolicyService;
    private final PaymentService paymentService;
    private final WalletService walletService;

    @Value("${app.checkout.base-url:http://localhost:8083}")
    private String checkoutBaseUrl;
    private final HttpClient callbackHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Transactional
    public CheckoutSessionResponse createSession(CreateCheckoutSessionRequest request) {
        PayCheckoutEntryMode resolvedEntryMode = resolveEntryMode(request.getEntryMode());
        PayCheckoutChannel resolvedChannel = resolveChannel(request.getChannel(), resolvedEntryMode);
        PayCheckoutIntegrationType integrationType = resolveIntegrationType(request.getIntegrationType());
        checkoutMerchantPolicyService.validate(request.getMerchantCode(), integrationType);

        PayCheckoutSession existingSession = payCheckoutSessionRepository
                .findByMerchantCodeAndExternalOrderId(request.getMerchantCode(), request.getExternalOrderId())
                .orElse(null);
        if (existingSession != null) {
            log.info("[Pay] 기존 체크아웃 세션 재사용. token={}, merchant={}, externalOrderId={}",
                    existingSession.getSessionToken(), existingSession.getMerchantCode(), existingSession.getExternalOrderId());
            return toResponse(existingSession);
        }

        // IN_APP_CODE는 금액 0 허용, 그 외는 1원 이상 필수
        if (resolvedEntryMode != PayCheckoutEntryMode.IN_APP_CODE && (request.getAmount() == null || request.getAmount() < 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero for merchant checkout");
        }

        Long resolvedPayUserId = resolvePayUserId(request, integrationType);
        Long resolvedPayAccountId = resolvePayAccountId(resolvedPayUserId);

        PayCheckoutSession session = new PayCheckoutSession();
        session.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
        session.setMerchantCode(request.getMerchantCode());
        session.setPayUserId(resolvedPayUserId);
        session.setPayAccountId(resolvedPayAccountId);
        session.setExternalOrderId(request.getExternalOrderId());
        session.setTitle(request.getTitle());
        session.setAmount(request.getAmount());
        session.setPointAmount(resolveNonNegativeAmount(request.getPointAmount()));
        session.setCouponDiscountAmount(resolveNonNegativeAmount(request.getCouponDiscountAmount()));
        session.setPayProductId(request.getPayProductId());
        session.setSuccessUrl(request.getSuccessUrl());
        session.setFailUrl(request.getFailUrl());
        session.setEntryMode(resolvedEntryMode);
        session.setChannel(resolvedChannel);
        session.setIntegrationType(integrationType);
        session.setStatus(PayCheckoutSessionStatus.CREATED);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(SESSION_EXPIRE_MINUTES));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        // 코드 결제 진입 토큰 발급
        if (resolvedEntryMode == PayCheckoutEntryMode.IN_APP_CODE) {
            session.setOneTimeToken("GP-CODE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            session.setTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_TOKEN_EXPIRE_MINUTES));
        }

        payCheckoutSessionRepository.save(session);
        log.info("[Pay] 체크아웃 세션 생성. token={}, merchant={}, integrationType={}, payUserId={}, amount={}",
                session.getSessionToken(), session.getMerchantCode(), session.getIntegrationType(), session.getPayUserId(), session.getAmount());

        return toResponse(session);
    }

    @Transactional
    public CheckoutSessionResponse createSessionByCode(CreateCheckoutByCodeRequest request) {
        PayCheckoutSession codeSession = payCheckoutSessionRepository.findByOneTimeToken(request.getCodeToken())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Code token not found"));

        if (codeSession.getEntryMode() != PayCheckoutEntryMode.IN_APP_CODE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code token is not issued from IN_APP_CODE session");
        }
        if (codeSession.getStatus() != PayCheckoutSessionStatus.CREATED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code session is not active");
        }
        if (codeSession.getTokenExpiresAt() == null || LocalDateTime.now().isAfter(codeSession.getTokenExpiresAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code token has expired");
        }

        PayCheckoutSession merchantSession = new PayCheckoutSession();
        merchantSession.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
        merchantSession.setMerchantCode(request.getMerchantCode());
        merchantSession.setPayUserId(codeSession.getPayUserId());
        merchantSession.setPayAccountId(codeSession.getPayAccountId());
        merchantSession.setExternalOrderId(request.getExternalOrderId());
        merchantSession.setTitle(request.getTitle());
        merchantSession.setAmount(request.getAmount());
        merchantSession.setPointAmount(0);
        merchantSession.setCouponDiscountAmount(0);
        merchantSession.setPayProductId(null);
        merchantSession.setSuccessUrl(request.getSuccessUrl());
        merchantSession.setFailUrl(request.getFailUrl());
        merchantSession.setEntryMode(PayCheckoutEntryMode.MERCHANT_REDIRECT);
        merchantSession.setChannel(resolveChannel(request.getChannel(), PayCheckoutEntryMode.MERCHANT_REDIRECT));
        merchantSession.setIntegrationType(PayCheckoutIntegrationType.INTERNAL_TOKEN);
        merchantSession.setStatus(PayCheckoutSessionStatus.CREATED);
        merchantSession.setExpiresAt(LocalDateTime.now().plusMinutes(SESSION_EXPIRE_MINUTES));
        merchantSession.setCreatedAt(LocalDateTime.now());
        merchantSession.setUpdatedAt(LocalDateTime.now());
        payCheckoutSessionRepository.save(merchantSession);

        codeSession.consumeOneTimeToken();
        log.info("[Pay] 코드 세션 교환 완료. merchantSessionToken={}, merchantCode={}, amount={}, payUserId={}",
                merchantSession.getSessionToken(), merchantSession.getMerchantCode(), merchantSession.getAmount(), merchantSession.getPayUserId());

        CheckoutProcessResult processResult = processCheckout(merchantSession.getSessionToken(), true, null);
        log.info("[Pay] 코드 세션 즉시 결제 완료. merchantSessionToken={}, status={}, redirectUrl={}",
                merchantSession.getSessionToken(), processResult.status(), processResult.redirectUrl());

        return CheckoutSessionResponse.builder()
                .sessionToken(merchantSession.getSessionToken())
                .checkoutUrl(processResult.redirectUrl())
                .status(processResult.status())
                .expiresAt(merchantSession.getExpiresAt())
                .build();
    }

    @Transactional
    public Long bindSessionOwner(String sessionToken, Long loginPayUserId) {
        PayCheckoutSession session = getSessionByToken(sessionToken);

        if (session.getStatus() != PayCheckoutSessionStatus.CREATED) {
            return session.getPayUserId();
        }

        if (session.isExpired(LocalDateTime.now())) {
            session.markExpired();
            throw new ApiException(HttpStatus.BAD_REQUEST, "Checkout session has expired");
        }

        if (session.getPayUserId() == null) {
            Long accountId = resolvePayAccountId(loginPayUserId);
            session.setPayUserId(loginPayUserId);
            session.setPayAccountId(accountId);
            session.setUpdatedAt(LocalDateTime.now());
            log.info("[Pay] 세션 소유자 바인딩 완료. sessionToken={}, payUserId={}, payAccountId={}",
                    sessionToken, loginPayUserId, accountId);
            return loginPayUserId;
        }

        if (!session.getPayUserId().equals(loginPayUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Checkout session owner mismatch");
        }

        return session.getPayUserId();
    }

    @Transactional
    public CheckoutPageView getPageView(String sessionToken) {
        PayCheckoutSession session = getSessionByToken(sessionToken);

        if (session.getStatus() == PayCheckoutSessionStatus.CREATED && session.isExpired(LocalDateTime.now())) {
            session.markExpired();
        }

        if (session.getPayAccountId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Checkout session is not linked to payer");
        }

        PayAccount account = payAccountRepository.findById(session.getPayAccountId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        int finalPayableAmount = Math.max(session.getAmount() - session.getPointAmount() - session.getCouponDiscountAmount(), 0);
        int expectedAutoChargeAmount = Math.max(finalPayableAmount - account.getBalance(), 0);

        return CheckoutPageView.builder()
                .sessionToken(session.getSessionToken())
                .merchantCode(session.getMerchantCode())
                .title(session.getTitle())
                .amount(session.getAmount())
                .pointAmount(session.getPointAmount())
                .finalPayableAmount(finalPayableAmount)
                .walletBalance(account.getBalance())
                .expectedAutoChargeAmount(expectedAutoChargeAmount)
                .accountNumber(account.getAccountNumber())
                .bankCode(account.getBankCode())
                .ownerName(account.getOwnerName())
                .entryMode(session.getEntryMode().name())
                .channel(session.getChannel().name())
                .oneTimeToken(session.getOneTimeToken())
                .qrPayload(buildQrPayload(session))
                .status(session.getStatus().name())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    @Transactional
    public CheckoutProcessResult processCheckout(String sessionToken, boolean autoChargeIfInsufficient, String codeToken) {
        PayCheckoutSession session = getSessionByToken(sessionToken);
        validateProcessable(session);
        validateCodeTokenIfNeeded(session, codeToken);

        if (session.getPayUserId() == null || session.getPayAccountId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Checkout session is not linked to payer");
        }

        try {
            PayPayment payment = paymentService.createPayment(toCreatePaymentRequest(session), "checkout-create-" + sessionToken);
            boolean autoCharged = false;

            try {
                paymentService.completePayment(payment.getId(), "checkout-complete-" + sessionToken);
            } catch (ApiException ex) {
                if (ex.getStatus() == HttpStatus.BAD_REQUEST
                        && "Insufficient balance".equalsIgnoreCase(ex.getMessage())
                        && autoChargeIfInsufficient) {
                    log.info("[Pay] 잔액 부족 자동충전 시작. token={}, paymentId={}", sessionToken, payment.getId());
                    PayAccount account = payAccountRepository.findById(session.getPayAccountId())
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
                    int shortage = Math.max(session.getAmount() - account.getBalance(), 0);
                    if (shortage > 0) {
                        walletService.charge(session.getPayUserId(), shortage);
                        autoCharged = true;
                    }
                    paymentService.completePayment(payment.getId(), "checkout-complete-retry-" + sessionToken);
                } else {
                    throw ex;
                }
            }

            session.markCompleted(payment.getId(), autoCharged);
            log.info("[Pay] 체크아웃 결제 완료. token={}, paymentId={}, autoChargeUsed={}",
                    sessionToken, payment.getId(), autoCharged);

            String redirectUrl = buildSuccessRedirectUrl(session, payment.getId());
            if (session.getChannel() == PayCheckoutChannel.QR) {
                notifyMerchantCallback(redirectUrl);
            }

            return CheckoutProcessResult.builder()
                    .redirectUrl(redirectUrl)
                    .status("COMPLETED")
                    .build();
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null ? "결제 처리 중 오류 발생" : ex.getMessage();
            session.markFailed(errorMessage);
            log.error("[Pay] 체크아웃 결제 실패. token={}, message={}", sessionToken, errorMessage, ex);

            String redirectUrl = buildFailRedirectUrl(session, errorMessage);
            if (session.getChannel() == PayCheckoutChannel.QR) {
                notifyMerchantCallback(redirectUrl);
            }

            return CheckoutProcessResult.builder()
                    .redirectUrl(redirectUrl)
                    .status("FAILED")
                    .build();
        }
    }

    private void validateProcessable(PayCheckoutSession session) {
        if (session.getStatus() != PayCheckoutSessionStatus.CREATED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Checkout session is not processable");
        }
        if (session.isExpired(LocalDateTime.now())) {
            session.markExpired();
            throw new ApiException(HttpStatus.BAD_REQUEST, "Checkout session has expired");
        }
    }

    private void validateCodeTokenIfNeeded(PayCheckoutSession session, String codeToken) {
        if (session.getEntryMode() != PayCheckoutEntryMode.IN_APP_CODE) {
            return;
        }

        if (session.getOneTimeToken() == null || session.getTokenExpiresAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code token is not available");
        }
        if (LocalDateTime.now().isAfter(session.getTokenExpiresAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code token has expired");
        }
        if (codeToken == null || !session.getOneTimeToken().equals(codeToken)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid code token");
        }

        session.consumeOneTimeToken();
        log.info("[Pay] 코드 토큰 검증 완료. sessionToken={}", session.getSessionToken());
    }

    private PayCheckoutSession getSessionByToken(String sessionToken) {
        return payCheckoutSessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Checkout session not found"));
    }

    @Transactional(readOnly = true)
    public Long getSessionPayUserId(String sessionToken) {
        PayCheckoutSession session = getSessionByToken(sessionToken);
        return session.getPayUserId();
    }

    @Transactional(readOnly = true)
    public boolean requiresPayLogin(String sessionToken) {
        PayCheckoutSession session = getSessionByToken(sessionToken);

        if (session.getPayUserId() == null || session.getPayAccountId() == null) {
            return true;
        }

        if (session.getIntegrationType() == null) {
            return true;
        }

        return session.getIntegrationType() == PayCheckoutIntegrationType.PAY_LOGIN
                || session.getIntegrationType() == PayCheckoutIntegrationType.OAUTH;
    }

    @Transactional(readOnly = true)
    public CheckoutStatusSnapshot getStatusSnapshot(String sessionToken) {
        PayCheckoutSession session = getSessionByToken(sessionToken);
        return new CheckoutStatusSnapshot(
                session.getSessionToken(),
                session.getStatus().name(),
                session.getChannel().name(),
                buildSuccessRedirectUrl(session, session.getPaymentId()),
                buildFailRedirectUrl(session, session.getErrorMessage())
        );
    }

    @Transactional(readOnly = true)
    public MerchantQrPageView getMerchantQrPageView(String sessionToken) {
        PayCheckoutSession session = getSessionByToken(sessionToken);
        return new MerchantQrPageView(
                session.getSessionToken(),
                session.getTitle(),
                session.getStatus().name()
        );
    }

    @Transactional(readOnly = true)
    public String getQrPayload(String sessionToken) {
        PayCheckoutSession session = getSessionByToken(sessionToken);
        return buildQrPayload(session);
    }

    private CreatePaymentRequest toCreatePaymentRequest(PayCheckoutSession session) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPayUserId(session.getPayUserId());
        request.setPayAccountId(session.getPayAccountId());
        request.setExternalOrderId(session.getExternalOrderId());
        request.setPaymentType("WALLET");
        request.setPayProductId(session.getPayProductId());
        request.setTitle(session.getTitle());
        request.setAmount(session.getAmount());
        request.setPointAmount(resolveNonNegativeAmount(session.getPointAmount()));
        request.setCouponDiscountAmount(resolveNonNegativeAmount(session.getCouponDiscountAmount()));
        return request;
    }

    private int resolveNonNegativeAmount(Integer amount) {
        if (amount == null || amount < 0) {
            return 0;
        }
        return amount;
    }

    private PayCheckoutEntryMode resolveEntryMode(String rawEntryMode) {
        if (rawEntryMode == null || rawEntryMode.isBlank()) {
            return PayCheckoutEntryMode.MERCHANT_REDIRECT;
        }
        try {
            return PayCheckoutEntryMode.valueOf(rawEntryMode.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported entryMode");
        }
    }

    private PayCheckoutChannel resolveChannel(String rawChannel, PayCheckoutEntryMode entryMode) {
        if (rawChannel == null || rawChannel.isBlank()) {
            return entryMode == PayCheckoutEntryMode.IN_APP_CODE ? PayCheckoutChannel.QR : PayCheckoutChannel.REDIRECT;
        }
        try {
            return PayCheckoutChannel.valueOf(rawChannel.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported channel");
        }
    }

    private PayCheckoutIntegrationType resolveIntegrationType(String rawIntegrationType) {
        if (rawIntegrationType == null || rawIntegrationType.isBlank()) {
            return PayCheckoutIntegrationType.INTERNAL_TOKEN;
        }
        try {
            return PayCheckoutIntegrationType.valueOf(rawIntegrationType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported integrationType");
        }
    }

    private Long resolvePayUserId(CreateCheckoutSessionRequest request, PayCheckoutIntegrationType integrationType) {
        if (request.getPayUserId() != null) {
            return request.getPayUserId();
        }
        if (request.getMerchantUserKey() != null && !request.getMerchantUserKey().isBlank()) {
            try {
                Long externalUserId = Long.parseLong(request.getMerchantUserKey());
                return payUserRepository.findByExternalUserId(externalUserId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pay user not linked"))
                        .getId();
            } catch (NumberFormatException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "merchantUserKey format is invalid");
            }
        }

        if (integrationType == PayCheckoutIntegrationType.PAY_LOGIN || integrationType == PayCheckoutIntegrationType.OAUTH) {
            return null;
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "payUserId or merchantUserKey is required");
    }

    private Long resolvePayAccountId(Long payUserId) {
        if (payUserId == null) {
            return null;
        }
        PayAccount account = payAccountRepository.findByPayUserId(payUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        return account.getId();
    }

    private String buildQrPayload(PayCheckoutSession session) {
        return checkoutBaseUrl + "/pay/checkout/" + session.getSessionToken();
    }

    private CheckoutSessionResponse toResponse(PayCheckoutSession session) {
        return CheckoutSessionResponse.builder()
                .sessionToken(session.getSessionToken())
                .checkoutUrl(checkoutBaseUrl + "/pay/checkout/" + session.getSessionToken())
                .status(session.getStatus().name())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    private String buildSuccessRedirectUrl(PayCheckoutSession session, Long paymentId) {
        return UriComponentsBuilder.fromUriString(session.getSuccessUrl())
                .queryParam("orderId", session.getExternalOrderId())
                .queryParam("paymentId", paymentId)
                .queryParam("amount", session.getAmount())
                .queryParam("status", "COMPLETED")
                .build(true)
                .toUriString();
    }

    private String buildFailRedirectUrl(PayCheckoutSession session, String errorMessage) {
        String resolvedMessage = errorMessage == null || errorMessage.isBlank()
                ? "결제 처리 중 오류 발생"
                : errorMessage;
        return UriComponentsBuilder.fromUriString(session.getFailUrl())
                .queryParam("orderId", session.getExternalOrderId())
                .queryParam("status", "FAILED")
                .queryParam("message", URLEncoder.encode(resolvedMessage, StandardCharsets.UTF_8))
                .build(true)
                .toUriString();
    }

    private void notifyMerchantCallback(String callbackUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(callbackUrl))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<Void> response = callbackHttpClient.send(request, HttpResponse.BodyHandlers.discarding());
            log.info("[Pay] QR 콜백 전송 완료. url={}, statusCode={}", callbackUrl, response.statusCode());
        } catch (Exception ex) {
            log.warn("[Pay] QR 콜백 전송 실패. url={}, reason={}", callbackUrl, ex.getMessage());
        }
    }
}
