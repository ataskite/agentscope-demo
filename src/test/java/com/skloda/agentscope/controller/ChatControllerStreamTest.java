package com.skloda.agentscope.controller;

import com.skloda.agentscope.model.ChatRequest;
import com.skloda.agentscope.service.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControllerStreamTest {

    @Test
    void sendMessageCompletesWhenRuntimeEmitsDoneEvenIfRuntimeFluxStaysOpen() {
        ChatController controller = new ChatController();
        AgentService agentService = new StubAgentService(Flux.concat(
                Flux.just(Map.of("type", "text", "content", "hello")),
                Flux.just(Map.of("type", "done")),
                Flux.never()));
        ReflectionTestUtils.setField(controller, "agentService", agentService);

        ChatRequest request = new ChatRequest();
        request.setAgentId("chat-basic");
        request.setMessage("hi");

        List<ServerSentEvent<String>> events = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> controller.sendMessage(request).collectList().block());

        assertEquals(2, events.size());
        assertTrue(events.get(0).data().contains("\"type\":\"text\""));
        assertTrue(events.get(1).data().contains("\"type\":\"done\""));
    }

    private static class StubAgentService extends AgentService {
        private final Flux<Map<String, Object>> stream;

        StubAgentService(Flux<Map<String, Object>> stream) {
            super(null, null);
            this.stream = stream;
        }

        @Override
        public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                          String filePath, String fileName,
                                                          String sessionId,
                                                          java.util.List<ChatRequest.ImageFile> images,
                                                          ChatRequest.AudioFile audio) {
            return stream;
        }
    }
}
