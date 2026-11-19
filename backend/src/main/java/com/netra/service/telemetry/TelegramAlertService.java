package com.netra.service.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * High-priority Telegram Threat Alert Dispatcher.
 * Pushes instant network alerts and Kali Linux attack notifications
 * directly to the defender's Telegram channel or personal bot chat.
 */
@Service
public class TelegramAlertService {

    private static final Logger log = LoggerFactory.getLogger(TelegramAlertService.class);
    private static final File CONFIG_FILE = new File("telegram_config.json");

    private String botToken = "";
    private String chatId = "";
    private String apiBaseUrl = "https://api.telegram.org";
    private boolean enabled = true;

    private final HttpClient httpClient;

    public TelegramAlertService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        loadConfig();
    }

    private synchronized void loadConfig() {
        // 1. Check environment variables first
        String envToken = System.getenv("TELEGRAM_BOT_TOKEN");
        String envChatId = System.getenv("TELEGRAM_CHAT_ID");
        if (envToken != null && !envToken.isBlank()) {
            this.botToken = envToken.trim();
        }
        if (envChatId != null && !envChatId.isBlank()) {
            this.chatId = envChatId.trim();
        }

        // 2. Check persistent JSON config file
        if (CONFIG_FILE.exists()) {
            try {
                String content = Files.readString(CONFIG_FILE.toPath(), StandardCharsets.UTF_8);
                if (content.contains("\"botToken\"")) {
                    this.botToken = extractJsonValue(content, "botToken");
                }
                if (content.contains("\"chatId\"")) {
                    this.chatId = extractJsonValue(content, "chatId");
                }
                if (content.contains("\"enabled\"")) {
                    this.enabled = !"false".equalsIgnoreCase(extractJsonValue(content, "enabled"));
                }
                log.info("[Telegram] Config loaded successfully. Chat ID: {}", maskChatId(this.chatId));
            } catch (Exception e) {
                log.warn("[Telegram] Could not read config file: {}", e.getMessage());
            }
        }
    }

    public synchronized void updateConfig(String newBotToken, String newChatId, boolean newEnabled) {
        if (newBotToken != null && !newBotToken.isBlank()) {
            String trimmed = newBotToken.trim();
            if (trimmed.contains(":") && trimmed.matches("^\\d+:[A-Za-z0-9_-]+$")) {
                this.botToken = trimmed;
            } else if (this.botToken == null || this.botToken.isBlank()) {
                this.botToken = trimmed;
            } else {
                log.warn("[Telegram] Ignored invalid bot token update (likely password autofill): {}", maskToken(trimmed));
            }
        }
        if (newChatId != null && !newChatId.isBlank()) {
            this.chatId = newChatId.trim();
        }
        this.enabled = newEnabled;

        try {
            String json = String.format("{\n  \"botToken\": \"%s\",\n  \"chatId\": \"%s\",\n  \"enabled\": %b\n}",
                    escapeJson(this.botToken), escapeJson(this.chatId), this.enabled);
            Files.writeString(CONFIG_FILE.toPath(), json, StandardCharsets.UTF_8);
            log.info("[Telegram] Saved updated credentials to {}", CONFIG_FILE.getAbsolutePath());
        } catch (Exception e) {
            log.error("[Telegram] Failed saving config: {}", e.getMessage());
        }
    }

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("configured", isConfigured());
        res.put("enabled", enabled);
        res.put("chatId", maskChatId(chatId));
        res.put("rawChatId", chatId);
        res.put("botTokenMasked", maskToken(botToken));
        return res;
    }

    /**
     * Sends formatted threat notification to Telegram asynchronously.
     */
    public CompletableFuture<Boolean> sendThreatAlert(
            String threatType,
            String threatSummary,
            String severity,
            String attackerIp,
            String targetIp,
            List<?> portsScanned,
            int packetCount,
            String mitreTactic) {

        if (!isConfigured() || !enabled) {
            return CompletableFuture.completedFuture(false);
        }

        String emoji = switch (severity != null ? severity.toUpperCase() : "MEDIUM") {
            case "CRITICAL" -> "🚨🔥 [CRITICAL BREACH]";
            case "HIGH" -> "⚠️🛑 [HIGH THREAT]";
            default -> "🛡️⚡ [INCIDENT ALERT]";
        };

        StringBuilder sb = new StringBuilder();
        sb.append(emoji).append("\n\n");
        sb.append("<b>Threat:</b> ").append(escapeHtml(threatSummary)).append("\n");
        sb.append("<b>Type:</b> <code>").append(escapeHtml(threatType)).append("</code>\n");
        sb.append("<b>Severity:</b> ").append(escapeHtml(severity)).append("\n\n");

        sb.append("<b>Attacker:</b> <code>").append(escapeHtml(attackerIp)).append("</code> (Kali Linux)\n");
        sb.append("<b>Target Host:</b> <code>").append(escapeHtml(targetIp)).append("</code>\n");

        if (portsScanned != null && !portsScanned.isEmpty()) {
            sb.append("<b>Ports Probed:</b> <code>").append(escapeHtml(portsScanned.toString())).append("</code>\n");
        }
        if (packetCount > 0) {
            sb.append("<b>Volume:</b> ").append(packetCount).append(" wire packets intercepted\n");
        }
        if (mitreTactic != null && !mitreTactic.isBlank()) {
            sb.append("<b>MITRE ATT&amp;CK:</b> ").append(escapeHtml(mitreTactic)).append("\n");
        }

        sb.append("\n<i>NETRA Autonomous Interception • ").append(Instant.now().toString()).append("</i>");

        return dispatchTelegramMessage(sb.toString());
    }

    /**
     * Sends an immediate test alert to verify Telegram credentials.
     */
    public CompletableFuture<Boolean> sendTestAlert() {
        if (!isConfigured()) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("Telegram bot token and chat ID are required."));
        }

        String msg = "<b>NETRA TELEGRAM ALERT VERIFICATION</b>\n\n" +
                "Successfully connected to the NETRA Defense Intelligence Platform!\n\n" +
                "<b>Status:</b> Active & Monitoring\n" +
                "<b>Alert Delivery:</b> Real-time\n" +
                "When attacks are launched from Kali Linux, instant alerts will arrive here.";

        return dispatchTelegramMessage(msg);
    }

    private CompletableFuture<Boolean> dispatchTelegramMessage(String htmlBody) {
        if (!isConfigured())
            return CompletableFuture.completedFuture(false);

        String url = "https://api.telegram.org/bot" + this.botToken.trim() + "/sendMessage";
        String payload = String.format("{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"HTML\"}",
                escapeJson(this.chatId.trim()),
                escapeJson(htmlBody));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(6))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() == 200) {
                        log.info("[Telegram] Alert delivered successfully to chat {}", maskChatId(this.chatId));
                        return true;
                    } else {
                        log.warn("[Telegram] Delivery failed HTTP {}: {}", resp.statusCode(), resp.body());
                        return false;
                    }
                })
                .exceptionally(ex -> {
                    log.error("[Telegram] Error sending alert: {}", ex.getMessage());
                    return false;
                });
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10)
            return "Not Configured";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    private String maskChatId(String id) {
        if (id == null || id.isBlank())
            return "Not Configured";
        if (id.length() <= 4)
            return id;
        return id.substring(0, 2) + "****" + id.substring(id.length() - 2);
    }

    private String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String escapeHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String extractJsonValue(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1)
            return "";
        int colon = json.indexOf(":", idx);
        if (colon == -1)
            return "";
        int startQuote = json.indexOf("\"", colon);
        if (startQuote != -1) {
            int endQuote = json.indexOf("\"", startQuote + 1);
            if (endQuote != -1) {
                return json.substring(startQuote + 1, endQuote);
            }
        }
        // Boolean or number
        int comma = json.indexOf(",", colon);
        int brace = json.indexOf("}", colon);
        int end = (comma != -1 && comma < brace) ? comma : brace;
        if (end != -1) {
            return json.substring(colon + 1, end).trim();
        }
        return "";
    }
}
