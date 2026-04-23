package com.gorani.gorani_pay.controller;

import com.gorani.gorani_pay.dto.CheckoutPageView;
import com.gorani.gorani_pay.dto.CheckoutProcessResult;
import com.gorani.gorani_pay.dto.CheckoutSessionResponse;
import com.gorani.gorani_pay.dto.CheckoutStatusSnapshot;
import com.gorani.gorani_pay.dto.CreateCheckoutByCodeRequest;
import com.gorani.gorani_pay.dto.CreateCheckoutSessionRequest;
import com.gorani.gorani_pay.dto.CreateMyCodeCheckoutRequest;
import com.gorani.gorani_pay.dto.MerchantQrPageView;
import com.gorani.gorani_pay.exception.ApiException;
import com.gorani.gorani_pay.service.CheckoutSessionService;
import com.gorani.gorani_pay.service.PayLoginTokenService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/pay/checkout")
@RequiredArgsConstructor
public class PayCheckoutController {

    private final CheckoutSessionService checkoutSessionService;
    private final PayLoginTokenService payLoginTokenService;

    @GetMapping
    public ResponseEntity<String> checkoutHome() {
        String html = """
                <!doctype html>
                <html lang="ko">
                <head><meta charset="UTF-8"><title>GORANI PAY</title></head>
                <body style="font-family:Arial,sans-serif;padding:24px;">
                  <h2>GORANI PAY 결제 페이지</h2>
                  <p>가맹점 결제 요청 URL로 접속해 주세요.</p>
                </body>
                </html>
                """;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @PostMapping("/sessions")
    public CheckoutSessionResponse createSession(@Valid @RequestBody CreateCheckoutSessionRequest request) {
        log.info("[Pay] 체크아웃 세션 생성 요청. merchant={}, payUserId={}, amount={}, entryMode={}, integrationType={}",
                request.getMerchantCode(), request.getPayUserId(), request.getAmount(), request.getEntryMode(), request.getIntegrationType());
        return checkoutSessionService.createSession(request);
    }

    @PostMapping("/sessions/by-code")
    public CheckoutSessionResponse createSessionByCode(@Valid @RequestBody CreateCheckoutByCodeRequest request) {
        log.info("[Pay] 코드 기반 세션 생성 요청. merchant={}, amount={}, externalOrderId={}",
                request.getMerchantCode(), request.getAmount(), request.getExternalOrderId());
        return checkoutSessionService.createSessionByCode(request);
    }

    @PostMapping("/me/code-sessions")
    public CheckoutSessionResponse createMyCodeSession(
            @Valid @RequestBody CreateMyCodeCheckoutRequest request,
            HttpServletRequest servletRequest
    ) {
        Long loginPayUserId = payLoginTokenService.resolvePayUserId(servletRequest);
        if (loginPayUserId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Login required");
        }

        CreateCheckoutSessionRequest checkoutRequest = new CreateCheckoutSessionRequest();
        checkoutRequest.setMerchantCode(request.getMerchantCode());
        checkoutRequest.setPayUserId(loginPayUserId);
        checkoutRequest.setExternalOrderId("PAY-CODE-" + UUID.randomUUID().toString().replace("-", ""));
        checkoutRequest.setTitle(request.getTitle());
        checkoutRequest.setAmount(0);
        checkoutRequest.setSuccessUrl(request.getSuccessUrl());
        checkoutRequest.setFailUrl(request.getFailUrl());
        checkoutRequest.setEntryMode("IN_APP_CODE");
        checkoutRequest.setChannel(request.getChannel() == null || request.getChannel().isBlank() ? "QR" : request.getChannel());
        checkoutRequest.setIntegrationType("PAY_LOGIN");

        return checkoutSessionService.createSession(checkoutRequest);
    }

    @GetMapping("/{sessionToken}")
    public ResponseEntity<String> checkoutPage(@PathVariable String sessionToken, HttpServletRequest request) {
        Long loginPayUserId = payLoginTokenService.resolvePayUserId(request);
        boolean loginRequired = checkoutSessionService.requiresPayLogin(sessionToken);

        if (loginPayUserId == null && loginRequired) {
            String returnUrl = "/pay/checkout/" + sessionToken;
            String redirectUrl = "/pay/login?returnUrl=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .build();
        }

        if (loginPayUserId != null) {
            checkoutSessionService.bindSessionOwner(sessionToken, loginPayUserId);
        }
        CheckoutPageView view = checkoutSessionService.getPageView(sessionToken);

        String html = "IN_APP_CODE".equalsIgnoreCase(view.entryMode())
                ? renderInAppCodePage(view)
                : renderMerchantRedirectPage(view);

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping("/{sessionToken}/merchant-qr")
    public ResponseEntity<String> merchantQrPage(@PathVariable String sessionToken) {
        MerchantQrPageView view = checkoutSessionService.getMerchantQrPageView(sessionToken);
        String html = renderMerchantQrOnlyPage(view);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping("/{sessionToken}/status")
    public CheckoutStatusSnapshot checkoutStatus(@PathVariable String sessionToken) {
        return checkoutSessionService.getStatusSnapshot(sessionToken);
    }

    @PostMapping("/{sessionToken}/submit")
    public ResponseEntity<?> submitCheckout(
            @PathVariable String sessionToken,
            @RequestParam(name = "autoChargeIfInsufficient", defaultValue = "true") boolean autoChargeIfInsufficient,
            @RequestParam(name = "codeToken", required = false) String codeToken,
            HttpServletRequest request
    ) {
        Long loginPayUserId = payLoginTokenService.resolvePayUserId(request);
        boolean loginRequired = checkoutSessionService.requiresPayLogin(sessionToken);

        if (loginPayUserId == null && loginRequired) {
            String returnUrl = "/pay/checkout/" + sessionToken;
            String redirectUrl = "/pay/login?returnUrl=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .build();
        }

        if (loginPayUserId != null) {
            checkoutSessionService.bindSessionOwner(sessionToken, loginPayUserId);
        }
        CheckoutPageView pageView = checkoutSessionService.getPageView(sessionToken);
        CheckoutProcessResult result = checkoutSessionService.processCheckout(sessionToken, autoChargeIfInsufficient, codeToken);

        if ("QR".equalsIgnoreCase(pageView.channel())) {
            String orderId = extractQueryParam(result.redirectUrl(), "orderId");
            String paymentId = extractQueryParam(result.redirectUrl(), "paymentId");
            String status = result.status() == null ? "UNKNOWN" : result.status();
            String message = "COMPLETED".equalsIgnoreCase(status)
                    ? "결제가 완료되었습니다. 이전 창으로 돌아가세요."
                    : "결제에 실패했습니다. 이전 창으로 돌아가세요.";
            String html = renderQrCompletionPage(status, message, orderId, paymentId);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, result.redirectUrl())
                .build();
    }

    @GetMapping(value = "/{sessionToken}/barcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> barcodeImage(@PathVariable String sessionToken) {
        try {
            CheckoutPageView view = checkoutSessionService.getPageView(sessionToken);
            String token = view.oneTimeToken();
            if (token == null || token.isBlank()) {
                return ResponseEntity.notFound().build();
            }

            BitMatrix matrix = new MultiFormatWriter().encode(token, BarcodeFormat.CODE_128, 260, 70);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return ResponseEntity.ok(output.toByteArray());
        } catch (Exception ex) {
            log.error("[Pay] 바코드 생성 실패. sessionToken={}", sessionToken, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = "/{sessionToken}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrImage(@PathVariable String sessionToken) {
        try {
            String qrPayload = checkoutSessionService.getQrPayload(sessionToken);
            if (qrPayload == null || qrPayload.isBlank()) {
                return ResponseEntity.notFound().build();
            }

            BitMatrix matrix = new MultiFormatWriter().encode(qrPayload, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return ResponseEntity.ok(output.toByteArray());
        } catch (Exception ex) {
            log.error("[Pay] QR 생성 실패. sessionToken={}", sessionToken, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String renderMerchantRedirectPage(CheckoutPageView view) {
        String title = HtmlUtils.htmlEscape(view.title());
        String merchant = HtmlUtils.htmlEscape(view.merchantCode());
        String token = HtmlUtils.htmlEscape(view.sessionToken());
        String bankCode = HtmlUtils.htmlEscape(view.bankCode() == null ? "-" : view.bankCode());
        String accountNumber = HtmlUtils.htmlEscape(view.accountNumber() == null ? "-" : view.accountNumber());
        String ownerName = HtmlUtils.htmlEscape(view.ownerName() == null ? "-" : view.ownerName());

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>GORANI PAY CHECKOUT</title>
                </head>
                <body style="font-family:Arial,sans-serif;background:#f6f8fb;padding:24px;">
                  <div id="loadingOverlay" style="display:none;position:fixed;inset:0;background:rgba(15,23,42,0.45);z-index:999;align-items:center;justify-content:center;">
                    <div style="width:100%%;max-width:340px;background:#fff;border:1px solid #d9deea;border-radius:12px;padding:20px;text-align:center;box-shadow:0 8px 28px rgba(18,52,86,0.15);">
                      <div style="width:34px;height:34px;border:4px solid #d1ddff;border-top-color:#1f6feb;border-radius:50%%;animation:pay-spin 0.8s linear infinite;margin:0 auto 12px;"></div>
                      <p style="margin:0;font-size:16px;font-weight:700;color:#111827;">결제 처리 중입니다</p>
                      <p style="margin:8px 0 0;font-size:13px;color:#6b7280;">창을 닫지 말고 잠시만 기다려 주세요.</p>
                    </div>
                  </div>
                  <div style="max-width:480px;margin:0 auto;background:#fff;border:1px solid #d9deea;border-radius:12px;padding:20px;">
                    <h2>GORANI PAY 결제</h2>
                    <p>가맹점: %s</p>
                    <p>주문명: %s</p>
                    <p>결제금액: %s원</p>
                    <p>보유머니: %s원</p>
                    <div style="margin:12px 0;padding:12px;border:1px solid #d9deea;border-radius:10px;background:#f8fafc;">
                      <p style="margin:0 0 6px;font-size:13px;color:#334155;">연결 계좌 정보</p>
                      <p style="margin:0;font-size:13px;color:#0f172a;">은행: %s</p>
                      <p style="margin:4px 0 0;font-size:13px;color:#0f172a;">계좌번호: %s</p>
                      <p style="margin:4px 0 0;font-size:13px;color:#0f172a;">예금주: %s</p>
                    </div>
                    <form id="checkoutForm" method="post" action="/pay/checkout/%s/submit">
                      <label><input type="checkbox" name="autoChargeIfInsufficient" value="true" checked /> 잔액 부족 시 자동충전 후 결제</label>
                      <button id="submitButton" type="submit" style="display:block;margin-top:12px;width:100%%;padding:12px;border:none;border-radius:8px;background:#1f6feb;color:#fff;font-weight:700;cursor:pointer;">결제하기</button>
                    </form>
                  </div>
                  <style>
                    @keyframes pay-spin { to { transform: rotate(360deg); } }
                  </style>
                  <script>
                    const checkoutForm = document.getElementById('checkoutForm');
                    const submitButton = document.getElementById('submitButton');
                    const loadingOverlay = document.getElementById('loadingOverlay');
                    checkoutForm.addEventListener('submit', function () {
                      submitButton.disabled = true;
                      submitButton.style.opacity = '0.7';
                      submitButton.style.cursor = 'not-allowed';
                      loadingOverlay.style.display = 'flex';
                    });
                  </script>
                </body>
                </html>
                """.formatted(merchant, title, view.finalPayableAmount(), view.walletBalance(), bankCode, accountNumber, ownerName, token);
    }

    private String renderInAppCodePage(CheckoutPageView view) {
        String title = HtmlUtils.htmlEscape(view.title());
        String token = HtmlUtils.htmlEscape(view.sessionToken());

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>GORANI PAY QR 결제</title>
                </head>
                <body style="font-family:Arial,sans-serif;background:#f6f8fb;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:#fff;border:1px solid #d9deea;border-radius:12px;padding:20px;">
                    <h2>GORANI PAY QR 결제</h2>
                    <p>주문명: %s</p>
                    <div style="display:flex;gap:8px;margin:12px 0;">
                      <button id="tabQr" type="button" style="flex:1;padding:10px 12px;border:1px solid #c9d4ea;border-radius:8px;background:#1f6feb;color:#fff;font-weight:700;cursor:pointer;">QR/바코드</button>
                      <button id="tabScan" type="button" style="flex:1;padding:10px 12px;border:1px solid #c9d4ea;border-radius:8px;background:#fff;color:#334155;font-weight:700;cursor:pointer;">QR 스캔</button>
                    </div>

                    <section id="panelQr" style="display:flex;flex-direction:column;align-items:center;gap:10px;">
                      <img src="/pay/checkout/%s/barcode" alt="barcode" style="width:260px;height:70px;border:1px solid #e5e7eb;border-radius:6px;object-fit:contain;" />
                      <img src="/pay/checkout/%s/qr" alt="qr" style="width:200px;height:200px;border:1px solid #e5e7eb;border-radius:8px;" />
                      <p style="margin-top:4px;font-size:12px;color:#6b7280;">세션 토큰: %s</p>
                    </section>

                    <section id="panelScan" style="display:none;">
                      <video id="scanVideo" autoplay playsinline muted style="width:100%%;display:none;border:1px solid #d9deea;border-radius:8px;background:#0f172a;"></video>
                      <div id="scanReader" style="display:none;border:1px solid #d9deea;border-radius:8px;overflow:hidden;"></div>
                      <form method="post" action="/pay/checkout/%s/submit" style="margin-top:10px;">
                        <input id="codeTokenInput" name="codeToken" type="text" placeholder="스캔 코드 입력" style="width:100%%;padding:10px;border:1px solid #c9d4ea;border-radius:8px;" />
                        <input type="hidden" name="autoChargeIfInsufficient" value="true" />
                        <button id="startScanButton" type="button" style="width:100%%;margin-top:8px;padding:10px;border:none;border-radius:8px;background:#0ea5e9;color:#fff;font-weight:700;cursor:pointer;">카메라로 스캔</button>
                        <button type="submit" style="width:100%%;margin-top:8px;padding:11px;border:none;border-radius:8px;background:#1f6feb;color:#fff;font-weight:700;cursor:pointer;">결제 승인</button>
                      </form>
                      <p style="margin-top:8px;font-size:12px;color:#6b7280;">스캔이 어려우면 코드를 직접 입력 후 결제 승인 가능합니다.</p>
                    </section>
                  </div>
                  <script src="https://unpkg.com/html5-qrcode@2.3.8/html5-qrcode.min.js"></script>
                  <script>
                    const tabQr = document.getElementById('tabQr');
                    const tabScan = document.getElementById('tabScan');
                    const panelQr = document.getElementById('panelQr');
                    const panelScan = document.getElementById('panelScan');
                    const scanVideo = document.getElementById('scanVideo');
                    const scanReader = document.getElementById('scanReader');
                    const codeTokenInput = document.getElementById('codeTokenInput');
                    const startScanButton = document.getElementById('startScanButton');
                    let scanStream = null;
                    let scanTimer = null;
                    let html5Scanner = null;

                    function applyDecodedValue(decodedValue) {
                      const value = (decodedValue || '').trim();
                      if (!value) {
                        return;
                      }

                      if (value.includes('/pay/checkout/')) {
                        const redirectUrl = value.startsWith('/pay/checkout/')
                          ? window.location.origin + value
                          : value;
                        window.location.href = redirectUrl;
                        return;
                      }

                      codeTokenInput.value = value;
                    }

                    function activateQrTab() {
                      panelQr.style.display = 'flex';
                      panelScan.style.display = 'none';
                      tabQr.style.background = '#1f6feb';
                      tabQr.style.color = '#fff';
                      tabScan.style.background = '#fff';
                      tabScan.style.color = '#334155';
                      stopScan();
                    }

                    function activateScanTab() {
                      panelQr.style.display = 'none';
                      panelScan.style.display = 'block';
                      tabScan.style.background = '#1f6feb';
                      tabScan.style.color = '#fff';
                      tabQr.style.background = '#fff';
                      tabQr.style.color = '#334155';
                    }

                    async function stopScan() {
                      if (scanTimer) {
                        clearInterval(scanTimer);
                        scanTimer = null;
                      }
                      if (scanStream) {
                        scanStream.getTracks().forEach(track => track.stop());
                        scanStream = null;
                      }
                      if (scanVideo) {
                        scanVideo.srcObject = null;
                        scanVideo.style.display = 'none';
                      }
                      if (html5Scanner) {
                        try { await html5Scanner.stop(); } catch (e) {}
                        try { await html5Scanner.clear(); } catch (e) {}
                        html5Scanner = null;
                      }
                      if (scanReader) {
                        scanReader.style.display = 'none';
                        scanReader.innerHTML = '';
                      }
                    }

                    async function startBarcodeDetectorScan() {
                      if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || !window.BarcodeDetector) {
                        return false;
                      }
                      const supportedFormats = await BarcodeDetector.getSupportedFormats().catch(() => []);
                      const formats = ['qr_code', 'code_128', 'ean_13'].filter(format => supportedFormats.includes(format));
                      if (formats.length === 0) {
                        return false;
                      }

                      const detector = new BarcodeDetector({formats});
                      scanStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
                      scanVideo.srcObject = scanStream;
                      scanVideo.style.display = 'block';

                      scanTimer = setInterval(async function () {
                        try {
                          const codes = await detector.detect(scanVideo);
                          if (codes && codes.length > 0 && codes[0].rawValue) {
                            applyDecodedValue(codes[0].rawValue);
                            await stopScan();
                          }
                        } catch (e) {}
                      }, 250);
                      return true;
                    }

                    async function startHtml5Scan() {
                      if (!window.Html5Qrcode) {
                        return false;
                      }
                      scanReader.style.display = 'block';
                      html5Scanner = new Html5Qrcode('scanReader');
                      await html5Scanner.start(
                        { facingMode: 'environment' },
                        { fps: 10, qrbox: { width: 240, height: 240 } },
                        async function (decodedText) {
                          applyDecodedValue(decodedText);
                          await stopScan();
                        },
                        function () {}
                      );
                      return true;
                    }

                    tabQr.addEventListener('click', activateQrTab);
                    tabScan.addEventListener('click', activateScanTab);
                    startScanButton.addEventListener('click', async function () {
                      try {
                        const barcodeStarted = await startBarcodeDetectorScan();
                        if (!barcodeStarted) {
                          const html5Started = await startHtml5Scan();
                          if (!html5Started) {
                            alert('현재 브라우저는 QR 스캔을 지원하지 않습니다.');
                          }
                        }
                      } catch (e) {
                        alert('카메라 스캔 시작 실패');
                      }
                    });
                  </script>
                </body>
                </html>
                """.formatted(title, token, token, token, token);
    }

    private String renderMerchantQrOnlyPage(MerchantQrPageView view) {
        String token = HtmlUtils.htmlEscape(view.sessionToken());
        String title = HtmlUtils.htmlEscape(view.title());
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>GORANI PAY 결제 요청 QR</title>
                </head>
                <body style="font-family:Arial,sans-serif;background:#f6f8fb;padding:24px;">
                  <div style="max-width:520px;margin:0 auto;background:#fff;border:1px solid #d9deea;border-radius:12px;padding:20px;text-align:center;">
                    <h2 style="margin:0 0 10px;">결제 요청 QR</h2>
                    <p style="margin:0 0 10px;color:#475569;">고객이 아래 QR을 스캔하면 결제창으로 이동합니다.</p>
                    <p style="margin:0 0 12px;font-size:14px;color:#0f172a;">주문명: <strong>%s</strong></p>
                    <img src="/pay/checkout/%s/qr" alt="qr" style="width:260px;height:260px;border:1px solid #e5e7eb;border-radius:8px;background:#fff;" />
                    <p id="statusText" style="margin:14px 0 0;font-size:14px;color:#334155;">결제 대기 중</p>
                  </div>
                  <script>
                    const sessionToken = '%s';
                    const statusText = document.getElementById('statusText');
                    const timer = setInterval(async function () {
                      try {
                        const response = await fetch('/pay/checkout/' + sessionToken + '/status', { cache: 'no-store' });
                        if (!response.ok) { return; }
                        const data = await response.json();
                        if (data.status === 'COMPLETED') {
                          clearInterval(timer);
                          if (window.opener && !window.opener.closed) {
                            window.opener.postMessage({
                              source: 'gorani-pay',
                              type: 'PAYMENT_RESULT',
                              status: 'COMPLETED'
                            }, '*');
                          }
                          alert('결제가 완료되었습니다. 이전 창으로 돌아가세요.');
                          window.close();
                          return;
                        }
                        if (data.status === 'FAILED' || data.status === 'EXPIRED') {
                          clearInterval(timer);
                          if (window.opener && !window.opener.closed) {
                            window.opener.postMessage({
                              source: 'gorani-pay',
                              type: 'PAYMENT_RESULT',
                              status: 'FAILED'
                            }, '*');
                          }
                          alert('결제에 실패했습니다. 이전 창으로 돌아가세요.');
                          window.close();
                          return;
                        }
                        statusText.textContent = data.status === 'PENDING' ? '결제 진행 중' : '결제 대기 중';
                      } catch (e) {}
                    }, 1200);
                  </script>
                </body>
                </html>
                """.formatted(title, token, token);
    }

    private String renderQrCompletionPage(String status, String message, String orderId, String paymentId) {
        String escapedStatus = HtmlUtils.htmlEscape(status == null ? "UNKNOWN" : status);
        String escapedMessage = HtmlUtils.htmlEscape(message);
        String escapedOrderId = HtmlUtils.htmlEscape(orderId == null ? "" : orderId);
        String escapedPaymentId = HtmlUtils.htmlEscape(paymentId == null ? "" : paymentId);

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>GORANI PAY 결제 결과</title>
                </head>
                <body style="font-family:Arial,sans-serif;background:#f6f8fb;padding:24px;">
                  <div style="max-width:520px;margin:0 auto;background:#fff;border:1px solid #d9deea;border-radius:12px;padding:20px;">
                    <h2 style="margin:0 0 10px;">GORANI PAY 결제 결과</h2>
                    <p style="margin:0 0 8px;">상태: <strong>%s</strong></p>
                    <p style="margin:0 0 8px;">주문번호: <strong>%s</strong></p>
                    <p style="margin:0 0 14px;">결제번호: <strong>%s</strong></p>
                    <p style="margin:0;font-size:16px;font-weight:700;color:#0f172a;">%s</p>
                    <button onclick="closeAndReturn()" style="margin-top:14px;padding:10px 12px;border:none;border-radius:8px;background:#1f6feb;color:#fff;font-weight:700;cursor:pointer;">창 닫기</button>
                  </div>
                  <script>
                    function notifyOpener() {
                      if (window.opener && !window.opener.closed) {
                        window.opener.postMessage({
                          source: 'gorani-pay',
                          type: 'PAYMENT_RESULT',
                          status: '%s',
                          orderId: '%s',
                          paymentId: '%s'
                        }, '*');
                        try { window.opener.location.reload(); } catch (e) {}
                      }
                    }
                    function closeAndReturn() {
                      notifyOpener();
                      window.close();
                      setTimeout(function () {
                        if (!window.closed) {
                          history.back();
                        }
                      }, 150);
                    }
                    notifyOpener();
                  </script>
                </body>
                </html>
                """.formatted(
                escapedStatus,
                escapedOrderId,
                escapedPaymentId,
                escapedMessage,
                escapedStatus,
                escapedOrderId,
                escapedPaymentId
        );
    }

    private String extractQueryParam(String rawUrl, String key) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return null;
            }
            String[] chunks = query.split("&");
            for (String chunk : chunks) {
                String[] pair = chunk.split("=", 2);
                if (pair.length == 2 && key.equals(pair[0])) {
                    return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
