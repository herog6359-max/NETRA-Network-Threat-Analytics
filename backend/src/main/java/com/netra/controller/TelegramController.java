package com.netra.controller;

import com.netra.service.telemetry.TelegramAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

    private final TelegramAlertService telegramAlertService;

    public TelegramController(TelegramAlertService telegramAlertService) {
        this.telegramAlertService = telegramAlertService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(telegramAlertService.getStatus());
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> payload) {
        String botToken = (String) payload.getOrDefault("botToken", "");
        String chatId = (String) payload.getOrDefault("chatId", "");
        boolean enabled = payload.get("enabled") instanceof Boolean ? (Boolean) payload.get("enabled") : true;

        telegramAlertService.updateConfig(botToken, chatId, enabled);
        return ResponseEntity.ok(telegramAlertService.getStatus());
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTest() {
        if (!telegramAlertService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Telegram bot token and chat ID are required. Please configure them first."));
        }

        try {
            boolean sent = telegramAlertService.sendTestAlert().get();
            if (sent) {
                return ResponseEntity
                        .ok(Map.of("status", "SUCCESS", "message", "Test alert successfully sent to Telegram!"));
            } else {
                return ResponseEntity.status(502).body(Map.of(
                        "status", "FAILED",
                        "message", "Telegram API returned an error. Please verify your Bot Token and Chat ID."));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "ERROR",
                    "message", "Failed to communicate with Telegram: " + e.getMessage()));
        }
    }
}
