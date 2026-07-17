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
            JsonNode root = MAPPER.readTree(body);

            // 1. Handle Feishu URL verification challenge
            Optional<String> challenge = FeishuInboundMapper.extractUrlChallenge(root);
            if (challenge.isPresent()) {
                log.info("S14: Feishu URL verification challenge received");
                return Mono.just(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"challenge\":\"" + challenge.get() + "\"}"));
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
