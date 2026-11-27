package com.skloda.agentscope.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skloda.agentscope.service.AgentService;
import io.agentscope.extensions.channel.feishu.FeishuChannelProperties;
import io.agentscope.extensions.channel.feishu.FeishuCrypto;
import io.agentscope.extensions.channel.feishu.FeishuInboundMapper;
import io.agentscope.extensions.channel.feishu.FeishuOutboundClient;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.harness.agent.gateway.channel.InboundMessage;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * S14: Feishu (Lark) IM webhook controller.
 * <p>
 * Activated via {@code --spring.profiles.active=feishu}. Receives Feishu event callbacks
 * (URL verification + IM message events), routes the message to the configured default
 * agent via {@link AgentService}, and replies back to the Feishu chat using
 * {@link FeishuOutboundClient}.
 * <p>
 * This mirrors the {@code /chat/send} SSE flow but for IM: instead of streaming events
 * to a browser, it collects the terminal assistant text and posts it as a single Feishu
 * message. Session isolation uses the Feishu chatId ({@code sessionId = "feishu:" + chatId}).
 *
 * <h3>Endpoint</h3>
 * {@code POST /channel/feishu/webhook} - Feishu event subscription callback URL.
 */
@RestController
@Profile("feishu")
public class FeishuChannelController {

    private static final Logger log = LoggerFactory.getLogger(FeishuChannelController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FeishuChannelProperties properties;
    private final FeishuOutboundClient outboundClient;
    private final AgentService agentService;
    private final String defaultAgentId;

    public FeishuChannelController(FeishuChannelProperties properties,
                                   FeishuOutboundClient outboundClient,
                                   AgentService agentService,
                                   @Value("${agentscope.channel.feishu.default-agent-id:chat-basic}") String defaultAgentId) {
        this.properties = properties;
        this.outboundClient = outboundClient;
        this.agentService = agentService;
        this.defaultAgentId = defaultAgentId;
    }

    @PostMapping(value = "${agentscope.channel.feishu.callback-path:/channel/feishu/webhook}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> webhook(
            @RequestBody String body,
            @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Lark-Request-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Lark-Signature", required = false) String signature) {
        try {
            // P1-5 security fix: verify the request BEFORE parsing/processing.
            // 1) With encrypt-key configured, require a valid HMAC signature over the
            //    raw body (Feishu signs timestamp+nonce+encryptedBody with the key).
            // 2) Without encryption, fall back to the verification token carried in
            //    the event envelope (header.token for v2, root.token for v1).
            // 3) With neither credential configured, reject (fail-closed) — running a
            //    public webhook with no request authentication is not allowed.
            if (!isRequestAuthenticated(body, timestamp, nonce, signature)) {
                log.warn("S14: Feishu webhook request failed authentication (signature/token check)");
                return Mono.just(ResponseEntity.status(401).body("{\"error\":\"invalid signature or token\"}"));
            }

            JsonNode root = MAPPER.readTree(body);

            // 1. Handle Feishu URL verification challenge
            Optional<String> challenge = FeishuInboundMapper.extractUrlChallenge(root);
            if (challenge.isPresent()) {
                log.info("S14: Feishu URL verification challenge received");
                // P1-5: respond via ObjectMapper (the challenge value is attacker-influenced
                // and must never be concatenated into a JSON string literal)
                Map<String, String> challengeResponse = Map.of("challenge", challenge.get());
                return Mono.just(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(MAPPER.writeValueAsString(challengeResponse)));
            }

            // 2. Decrypt if encrypted (encrypt-key configured)
            JsonNode eventNode = root;
            if (properties.isEncrypted() && root.has("encrypt")) {
                FeishuCrypto crypto = new FeishuCrypto(properties.encryptKey());
                String decrypted = crypto.decrypt(root.get("encrypt").asText());
                eventNode = MAPPER.readTree(decrypted);
            }

            // 3. Map to InboundMessage
            FeishuInboundMapper mapper = new FeishuInboundMapper(defaultAgentId);
            Optional<InboundMessage> inboundOpt = mapper.map(eventNode);
            if (inboundOpt.isEmpty()) {
                log.debug("S14: Feishu event ignored (not a message event): {}", eventNode.path("type").asText());
                return Mono.just(ResponseEntity.ok().body("{}"));
            }

            InboundMessage inbound = inboundOpt.get();
            String messageText = extractText(inbound);
            if (messageText == null || messageText.isBlank()) {
                log.debug("S14: Feishu message has no text content, skipping");
                return Mono.just(ResponseEntity.ok().body("{}"));
            }

            String chatId = inbound.channelId() != null ? inbound.channelId() : inbound.senderId();
            String sessionId = "feishu:" + chatId;
            String userId = "feishu:" + inbound.senderId();
            log.info("S14: Feishu message received (chatId={}, user={}, text={}...)", chatId, userId,
                    messageText.length() > 50 ? messageText.substring(0, 50) : messageText);

            // 4. Route to agent (non-streaming collect: gather terminal assistant text)
            return agentService.createStreamFlux(
                            defaultAgentId, messageText, null, null,
                            sessionId, null, null, userId, null, null, null)
                    .reduce("", FeishuChannelController::accumulateAssistantText)
                    .flatMap(replyText -> {
                        if (replyText.isBlank()) {
                            replyText = "(agent returned no reply)";
                        }
                        // 5. Reply to Feishu
                        OutboundAddress addr = OutboundAddress.direct("feishu", chatId);
                        Msg replyMsg = Msg.builder()
                                .role(MsgRole.ASSISTANT)
                                .content(TextBlock.builder().text(replyText).build())
                                .build();
                        return outboundClient.send(addr, List.of(replyMsg))
                                .thenReturn(ResponseEntity.ok().body("{}"));
                    })
                    .onErrorResume(e -> {
                        log.error("S14: Feishu webhook processing failed", e);
                        return Mono.just(ResponseEntity.ok().body("{}"));
                    });
        } catch (Exception e) {
            log.error("S14: Feishu webhook parse error", e);
            return Mono.just(ResponseEntity.ok().body("{}"));
        }
    }

    /** Extract plain text from the inbound message's first Msg. */
    private static String extractText(InboundMessage inbound) {
        if (inbound.messages() == null || inbound.messages().isEmpty()) return null;
        Msg msg = inbound.messages().get(0);
        return msg.getTextContent();
    }

    /**
     * P1-5 security fix: authenticate the webhook request before any processing.
     * <ul>
     *   <li>encrypt-key configured → mandatory HMAC signature verification over the
     *       raw body via {@link FeishuCrypto#verifySignature} (missing/invalid → reject)</li>
     *   <li>no encryption → verification-token comparison against the envelope token
     *       (v2 events: {@code header.token}; v1 url_verification: {@code token})</li>
     *   <li>neither credential configured → reject (fail-closed)</li>
     * </ul>
     */
    private boolean isRequestAuthenticated(String body, String timestamp, String nonce, String signature) {
        boolean hasEncryptKey = properties.isEncrypted();
        if (hasEncryptKey) {
            if (timestamp == null || nonce == null || signature == null || signature.isBlank()) {
                return false;
            }
            return new FeishuCrypto(properties.encryptKey())
                    .verifySignature(timestamp, nonce, body, signature);
        }

        String verificationToken = properties.verificationToken();
        if (verificationToken == null || verificationToken.isBlank()) {
            log.warn("S14: Feishu webhook has neither encrypt-key nor verification-token configured; "
                    + "rejecting unauthenticated event (P1-5 fail-closed). Configure FEISHU_ENCRYPT_KEY "
                    + "or FEISHU_VERIFICATION_TOKEN.");
            return false;
        }

        try {
            JsonNode root = MAPPER.readTree(body);
            String token = root.path("header").path("token").asText("");
            if (token.isBlank()) {
                token = root.path("token").asText("");
            }
            return !token.isBlank() && constantTimeEquals(token, verificationToken);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Accumulate assistant text from SSE events. The AgentService flux emits
     * Map<String,Object> events; we collect "text" type deltas and the final
     * "agent_result_text" payload.
     */
    @SuppressWarnings("unchecked")
    private static String accumulateAssistantText(String acc, Object eventObj) {
        if (!(eventObj instanceof Map)) return acc;
        Map<String, Object> event = (Map<String, Object>) eventObj;
        String type = String.valueOf(event.getOrDefault("type", ""));
        if ("text".equals(type)) {
            return acc + event.getOrDefault("content", "");
        }
        if ("agent_result_text".equals(type)) {
            String content = String.valueOf(event.getOrDefault("content", ""));
            return content.isBlank() ? acc : content;
        }
        return acc;
    }
}
