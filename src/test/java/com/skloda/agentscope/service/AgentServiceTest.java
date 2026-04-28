package com.skloda.agentscope.service;

import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.model.ChatRequest;
import com.skloda.agentscope.runtime.AgentRuntimeFactory;
import com.skloda.agentscope.runtime.StreamingAgentRuntime;
import io.agentscope.core.message.AudioBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
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

    private static class StubRuntimeFactory extends AgentRuntimeFactory {
        private final StreamingAgentRuntime runtime;

        StubRuntimeFactory(StreamingAgentRuntime runtime) {
            super(null, null);
            this.runtime = runtime;
        }

        @Override
        public StreamingAgentRuntime createRuntime(String agentId) {
            return runtime;
        }
    }

    private static class CapturingRuntime implements StreamingAgentRuntime {
        private Msg captured;
        private final ObservabilityHook hook = new ObservabilityHook();

        @Override
        public Flux<Map<String, Object>> stream(Msg userMsg) {
            this.captured = userMsg;
            return Flux.just(Map.of("type", "done"));
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
