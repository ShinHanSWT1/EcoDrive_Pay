package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.entity.PayWebhookLog;
import com.gorani.gorani_pay.repository.PayWebhookLogRepository;
import com.gorani.gorani_pay.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WebhookLogService {

    private final PayWebhookLogRepository webhookLogRepository;

    public void record(String eventType, String externalOrderId, Object payload) {
        PayWebhookLog log = new PayWebhookLog();
        log.setEventType(eventType);
        log.setExternalOrderId(externalOrderId);
        log.setPayload(JsonUtil.toJson(payload));
        log.setDeliveredAt(LocalDateTime.now());
        log.setResponseCode(200);
        log.setStatus("SUCCESS");

        webhookLogRepository.save(log);
    }

    public List<PayWebhookLog> getLogs(String externalOrderId) {
        if (externalOrderId == null || externalOrderId.isBlank()) {
            return webhookLogRepository.findAllByOrderByIdDesc();
        }
        return webhookLogRepository.findByExternalOrderIdOrderByIdDesc(externalOrderId);
    }
}
