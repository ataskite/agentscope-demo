package com.skloda.agentscope.config;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * S12: A2A Protocol client demo.
 * <p>
 * Activated via {@code --spring.profiles.active=a2a}. Builds an {@link A2aAgent} client
 * that resolves the local A2A server's AgentCard (exposed by {@link A2aServerConfig} via
 * the auto-configured {@code AgentCardController} at {@code /.well-known/agent-card.json})
 * and invokes it in a self-loop.
 * <p>
 * This demonstrates: one agent (the server, wrapping {@code chat-basic}) registers a
 * capability, and another (this client) discovers and calls it via the A2A protocol.
 *
 * <h3>Usage</h3>
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=a2a"
 * # Watch the logs for the A2A client -> server self-loop result.
 * </pre>
 */
@Component
@Profile("a2a")
@Order(10)
public class A2aClientDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(A2aClientDemoRunner.class);

    private final String serverBaseUrl;

    public A2aClientDemoRunner(
            @Value("${server.port:8081}") int port,
            @Value("${agentscope.a2a.client.base-url:http://localhost:${server.port}}") String baseUrl) {
        // Default to localhost:port (self-loop); overridable via agentscope.a2a.client.base-url
        this.serverBaseUrl = baseUrl.contains("${server.port}")
                ? "http://localhost:" + port
                : baseUrl;
    }

    @Override
    public void run(String... args) {
        log.info("S12: A2A client demo starting - resolving AgentCard from {}", serverBaseUrl);
        try {
            WellKnownAgentCardResolver resolver = WellKnownAgentCardResolver.builder()
                    .baseUrl(serverBaseUrl)
                    .relativeCardPath("/.well-known/agent-card.json")
                    .build();

            A2aAgent remoteAgent = A2aAgent.builder()
                    .name("a2a-client-demo")
                    .agentCardResolver(resolver)
                    .build();

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text("你好，请用一句话介绍你自己。我是通过 A2A 协议远程调用你的。").build())
                    .build();

            log.info("S12: A2A client sending task to remote agent...");
            Msg reply = remoteAgent.call(List.of(userMsg)).block();

            String replyText = reply != null ? reply.getTextContent() : "(no reply)";
            log.info("S12: A2A client received reply from remote agent: {}", replyText);
            log.info("S12: A2A self-loop demo complete. Server agent was discovered via AgentCard and invoked via A2A JSON-RPC.");
        } catch (Exception e) {
            log.error("S12: A2A client demo failed (is the A2A server ready? self-loop needs the server endpoint live): {}", e.getMessage(), e);
        }
    }
}
