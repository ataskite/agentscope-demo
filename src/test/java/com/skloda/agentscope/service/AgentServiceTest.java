package com.skloda.agentscope.service;

import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.model.ChatRequest;
import com.skloda.agentscope.model.WorkflowRunSnapshot;
import com.skloda.agentscope.runtime.AgentRuntimeFactory;
import com.skloda.agentscope.runtime.StreamingAgentRuntime;
import io.agentscope.core.message.AudioBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.memory.InMemoryMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AgentServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void statelessTextMessageIsSentToRuntime() {
        CapturingRuntime runtime = new CapturingRuntime();
        AgentService service = new AgentService(new StubRuntimeFactory(runtime), null);

        List<Map<String, Object>> events = service.createStreamFlux(
                "chat-basic", "hello", null, null, null).collectList().block();

        assertEquals(List.of(Map.of("type", "done")), events);
        assertEquals("hello", runtime.captured.getTextContent());
    }

    @Test
    void streamEventsAreStoredForWorkflowReplay() {
        CapturingRuntime runtime = new CapturingRuntime();
        WorkflowRunService workflowRuns = new WorkflowRunService();
        AgentService service = new AgentService(new StubRuntimeFactory(runtime), null, workflowRuns);

        service.createStreamFlux("contract-review-workflow", "review this",
                null, null, null).collectList().block();

        var snapshot = workflowRuns.listRuns().get(0);
        assertEquals("contract-review-workflow", snapshot.getAgentId());
        assertEquals(null, snapshot.getSessionId());
        assertEquals("review this", snapshot.getInputPreview());
        assertEquals(WorkflowRunSnapshot.Status.COMPLETED, snapshot.getStatus());
        assertEquals(List.of(Map.of("type", "done")), snapshot.getEvents());
    }

    @Test
    void fileInfoIsPrependedToTextMessage() {
        CapturingRuntime runtime = new CapturingRuntime();
        AgentService service = new AgentService(new StubRuntimeFactory(runtime), null);

        service.createStreamFlux("task-document-analysis", "summarize it",
                "/tmp/report.pdf", "report.pdf", null).blockLast();

        assertEquals("[用户上传了文件: report.pdf, 路径: /tmp/report.pdf]\n\nsummarize it",
                runtime.captured.getTextContent());
    }

    @Test
    void imageMessageIncludesPrependedTextAndImageBlock() throws Exception {
        Path image = tempDir.resolve("image.png");
        Files.write(image, new byte[] {1, 2, 3});
        CapturingRuntime runtime = new CapturingRuntime();
        AgentService service = new AgentService(new StubRuntimeFactory(runtime), null);

        service.createStreamFlux("vision-analyzer", "what is this",
                "/tmp/source.docx", "source.docx", null,
                List.of(new ChatRequest.ImageFile(image.toString(), "image.png")), null).blockLast();

        assertEquals(2, runtime.captured.getContent().size());
        TextBlock text = assertInstanceOf(TextBlock.class, runtime.captured.getContent().get(0));
        assertEquals("[用户上传了文件: source.docx, 路径: /tmp/source.docx]\n\nwhat is this", text.getText());
        assertInstanceOf(ImageBlock.class, runtime.captured.getContent().get(1));
    }

    @Test
    void audioMessageUsesAudioOnlyPayload() throws Exception {
        Path audio = tempDir.resolve("audio.mp3");
        Files.write(audio, new byte[] {1, 2, 3});
        CapturingRuntime runtime = new CapturingRuntime();
        AgentService service = new AgentService(new StubRuntimeFactory(runtime), null);

        service.createStreamFlux("voice-assistant", "ignored",
                null, null, null, null,
                new ChatRequest.AudioFile(audio.toString(), "audio.mp3")).blockLast();

        assertEquals(1, runtime.captured.getContent().size());
        assertInstanceOf(AudioBlock.class, runtime.captured.getContent().get(0));
    }

    @Test
    void sessionStreamRecordsUserAndAssistantTranscriptForAgent() {
        CapturingRuntime runtime = new CapturingRuntime(
                Flux.just(
                        Map.of("type", "text", "content", "he"),
                        Map.of("type", "text", "content", "llo"),
                        Map.of("type", "done")));
        ChatHistoryRepository historyRepository = new InMemoryChatHistoryRepository();
        SessionManagerService sessionManager = new StubSessionManagerService(
                new SessionManagerService.SessionContext(
                        "session-1", "chat-basic", null, new InMemoryMemory(), null));

        AgentService service = new AgentService(
                new StubRuntimeFactory(runtime), sessionManager, new WorkflowRunService(), historyRepository);

        service.createStreamFlux("chat-basic", "hello", null, null, "session-1").collectList().block();

        List<com.skloda.agentscope.model.ChatMessage> messages =
                historyRepository.findByAgentId("chat-basic");
        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).getRole());
        assertEquals("hello", messages.get(0).getContent());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("hello", messages.get(1).getContent());
    }

    @Test
    void sessionStreamRecordsThinkingTranscriptForAssistantMessage() {
        CapturingRuntime runtime = new CapturingRuntime(
                Flux.just(
                        Map.of("type", "thinking", "content", "step one\n"),
                        Map.of("type", "reasoning_text", "content", "step two"),
                        Map.of("type", "text", "content", "answer"),
                        Map.of("type", "done")));
        ChatHistoryRepository historyRepository = new InMemoryChatHistoryRepository();
        SessionManagerService sessionManager = new StubSessionManagerService(
                new SessionManagerService.SessionContext(
                        "session-1", "chat-basic", null, new InMemoryMemory(), null));

        AgentService service = new AgentService(
                new StubRuntimeFactory(runtime), sessionManager, new WorkflowRunService(), historyRepository);

        service.createStreamFlux("chat-basic", "hello", null, null, "session-1").collectList().block();

        List<com.skloda.agentscope.model.ChatMessage> messages =
                historyRepository.findByAgentId("chat-basic");
        assertEquals("answer", messages.get(1).getContent());
        assertEquals("step one\nstep two", messages.get(1).getThinkingContent());
    }

    private static class StubRuntimeFactory extends AgentRuntimeFactory {
        private final StreamingAgentRuntime runtime;

        StubRuntimeFactory(StreamingAgentRuntime runtime) {
            super(null, null, null);
            this.runtime = runtime;
        }

        @Override
        public StreamingAgentRuntime createRuntime(String agentId) {
            return runtime;
        }

        @Override
        public StreamingAgentRuntime createRuntimeWithMemory(String agentId, io.agentscope.core.memory.Memory memory) {
            return runtime;
        }
    }

    private static class StubSessionManagerService extends SessionManagerService {
        private final SessionContext context;

        StubSessionManagerService(SessionContext context) {
            super(null, null, "/tmp/agentscope-test-sessions");
            this.context = context;
        }

        @Override
        public SessionContext getOrCreateSession(String sessionId, String agentId) {
            return context;
        }

        @Override
        public void saveSession(String sessionId) {
            context.touch();
        }
    }

    private static class CapturingRuntime implements StreamingAgentRuntime {
        private Msg captured;
        private final ObservabilityHook hook = new ObservabilityHook();
        private final Flux<Map<String, Object>> events;

        CapturingRuntime() {
            this(Flux.just(Map.of("type", "done")));
        }

        CapturingRuntime(Flux<Map<String, Object>> events) {
            this.events = events;
        }

        @Override
        public Flux<Map<String, Object>> stream(Msg userMsg) {
            this.captured = userMsg;
            return events;
        }

        @Override
        public ObservabilityHook getHook() {
            return hook;
        }

        @Override
        public void close() {
            // No resources in this fake runtime.
        }
    }
}
