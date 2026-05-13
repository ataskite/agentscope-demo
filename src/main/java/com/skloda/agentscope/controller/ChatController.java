package com.skloda.agentscope.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.SamplePrompt;
import com.skloda.agentscope.model.*;
import com.skloda.agentscope.runtime.AgentRuntime;
import com.skloda.agentscope.service.AgentService;
import com.skloda.agentscope.service.ApprovalService;
import com.skloda.agentscope.service.ChatHistoryRepository;
import com.skloda.agentscope.service.SessionManagerService;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chat Controller with reactive SSE streaming + session management.
 */
@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SSE_EVENT_NAME = "message";

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentConfigService agentConfigService;

    @Autowired
    private SessionManagerService sessionManagerService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ChatHistoryRepository chatHistoryRepository;

    @GetMapping("/")
    public String chat() {
        return "chat";
    }

    // ---- SSE helpers ----

    private ServerSentEvent<String> sseEvent(Object payload) {
        try {
            return ServerSentEvent.<String>builder()
                    .event(SSE_EVENT_NAME)
                    .data(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            log.error("Failed to serialize SSE payload", e);
            return ServerSentEvent.<String>builder()
                    .event(SSE_EVENT_NAME)
                    .data(toJsonString(ChatEvent.error("Serialization error")))
                    .build();
        }
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize object", e);
            return "{\"type\":\"error\",\"message\":\"Serialization error\"}";
        }
    }

    private ServerSentEvent<String> sseDone() {
        return sseEvent(ChatEvent.done());
    }

    private ServerSentEvent<String> sseError(String message) {
        return sseEvent(ChatEvent.error(message));
    }

    private Flux<ServerSentEvent<String>> errorAndDone(String message) {
        return Flux.just(sseError(message), sseDone());
    }

    // ---- Chat Endpoints ----

    /**
     * Send message endpoint — returns SSE stream directly.
     * Supports session-aware mode via sessionId in request body.
     * Supports multi-modal input (images/audio).
     */
    @PostMapping(value = "/chat/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<String>> sendMessage(@RequestBody ChatRequest request) {
        String message = request.getMessage();
        // Allow empty message if images or audio are provided
        if ((message == null || message.isBlank()) &&
            (request.getImages() == null || request.getImages().isEmpty()) &&
            request.getAudio() == null) {
            return errorAndDone("Message cannot be empty");
        }

        // Inject sessionId into the first SSE event so frontend can track it
        String sessionId = request.getSessionId();

        return agentService.createStreamFlux(
                        request.getAgentId(), message,
                        request.getFilePath(), request.getFileName(),
                        sessionId,
                        request.getImages(),
                        request.getAudio())
                .takeUntil(this::isDoneEvent)
                .map(this::sseEvent)
                .onErrorResume(e -> {
                    log.error("Agent stream error", e);
                    String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    return errorAndDone(errMsg);
                });
    }

    // ---- HITL Approval Endpoint ----

    /**
     * Handle human-in-the-loop approval or rejection.
     * Resumes the paused agent if approved, or sends cancellation result if rejected.
     */
    @PostMapping(value = "/chat/approve", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<String>> handleApproval(@RequestBody ApprovalRequest request) {
        PendingApproval pa = approvalService.getPendingApproval(request.getApprovalId());
        if (pa == null) {
            return errorAndDone("Approval request not found or expired: " + request.getApprovalId());
        }

        approvalService.removePendingApproval(request.getApprovalId());

        if (!request.isApproved()) {
            Msg rejectionMsg = buildRejectionMessage(pa.getPendingToolCalls(), request.getReason());
            AgentRuntime resumeRuntime = new AgentRuntime(pa.getAgent(), pa.getHook());
            return resumeRuntime.stream(rejectionMsg, true)
                    .takeUntil(this::isDoneEvent)
                    .map(this::sseEvent)
                    .onErrorResume(e -> errorAndDone(e.getMessage()));
        }

        // Approved: resume with null message to let tools execute
        AgentRuntime resumeRuntime = new AgentRuntime(pa.getAgent(), pa.getHook());
        return resumeRuntime.stream(null, true)
                .takeUntil(this::isDoneEvent)
                .map(this::sseEvent)
                .onErrorResume(e -> errorAndDone(e.getMessage()));
    }

    private boolean isDoneEvent(Map<String, Object> event) {
        return event != null && "done".equals(event.get("type"));
    }

    private Msg buildRejectionMessage(List<ToolUseBlock> pendingToolCalls, String reason) {
        String rejectionText = reason != null && !reason.isBlank() ? reason : "Operation rejected by user";
        List<ContentBlock> results = pendingToolCalls.stream()
                .map(tub -> ToolResultBlock.builder()
                        .id(tub.getId())
                        .name(tub.getName())
                        .output(TextBlock.builder()
                                .text("Tool call rejected by user: " + rejectionText)
                                .build())
                        .build())
                .map(ContentBlock.class::cast)
                .toList();

        return Msg.builder()
                .content(results)
                .build();
    }

    // ---- Session Endpoints ----

    /**
     * List all sessions.
     */
    @GetMapping("/api/sessions")
    @ResponseBody
    public List<SessionInfo> listSessions() {
        return sessionManagerService.listSessions().stream()
                .peek(info -> info.setMessageCount(
                        chatHistoryRepository.findByAgentId(info.getAgentId()).size()))
                .toList();
    }

    /**
     * Create a new session for the given agent.
     */
    @PostMapping("/api/sessions")
    @ResponseBody
    public ResponseEntity<?> createSession(@RequestBody Map<String, String> body) {
        String agentId = body.getOrDefault("agentId", "chat-basic");
        try {
            SessionManagerService.SessionContext ctx = sessionManagerService.getOrCreateSessionForAgent(agentId);
            SessionInfo info = new SessionInfo();
            info.setSessionId(ctx.getSessionId());
            info.setAgentId(ctx.getAgentId());
            AgentConfig cfg = agentConfigService.findAgentConfig(ctx.getAgentId()).orElse(null);
            info.setAgentName(cfg != null ? cfg.getName() : ctx.getAgentId());
            info.setMessageCount(chatHistoryRepository.findByAgentId(ctx.getAgentId()).size());
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Failed to create session for agent: {}", agentId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a session.
     */
    @DeleteMapping("/api/sessions/{sessionId}")
    @ResponseBody
    public ResponseEntity<?> deleteSession(@PathVariable String sessionId) {
        SessionManagerService.SessionContext ctx = sessionManagerService.getSession(sessionId);
        if (ctx != null) {
            chatHistoryRepository.clear(ctx.getAgentId());
        }
        sessionManagerService.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    /**
     * Load the in-memory chat transcript for an agent.
     */
    @GetMapping("/api/agents/{agentId}/messages")
    @ResponseBody
    public List<ChatMessage> listAgentMessages(@PathVariable String agentId) {
        return chatHistoryRepository.findByAgentId(agentId);
    }

    // ---- Agent Config Endpoints ----

    /**
     * List all available agent configurations.
     */
    @GetMapping("/api/agents")
    @ResponseBody
    public List<AgentConfig> listAgents() {
        return agentConfigService.getAllAgents().stream()
                .map(this::toAgentConfigPreview)
                .toList();
    }

    /**
     * Get a specific agent configuration by agentId.
     */
    @GetMapping("/api/agents/{agentId}")
    @ResponseBody
    public ResponseEntity<?> getAgentConfig(@PathVariable String agentId) {
        Optional<AgentConfig> config = agentConfigService.findAgentConfig(agentId);
        if (config.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Agent not found: " + agentId));
        }
        return ResponseEntity.ok(toAgentConfigPreview(config.get()));
    }

    /**
     * Get the full text for one sample prompt. Agent lists only expose previews.
     */
    @GetMapping("/api/agents/{agentId}/sample-prompts/{index}")
    @ResponseBody
    public ResponseEntity<?> getSamplePrompt(@PathVariable String agentId, @PathVariable int index) {
        Optional<AgentConfig> config = agentConfigService.findAgentConfig(agentId);
        if (config.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Agent not found: " + agentId));
        }
        List<SamplePrompt> prompts = config.get().getSamplePrompts();
        if (index < 0 || index >= prompts.size()) {
            return ResponseEntity.status(404).body(Map.of("error", "Sample prompt not found: " + index));
        }
        SamplePrompt sample = prompts.get(index);
        return ResponseEntity.ok(Map.of(
                "agentId", agentId,
                "index", index,
                "prompt", sample.getPrompt() != null ? sample.getPrompt() : "",
                "expectedBehavior", sample.getExpectedBehavior() != null ? sample.getExpectedBehavior() : ""
        ));
    }

    private AgentConfig toAgentConfigPreview(AgentConfig source) {
        AgentConfig target = new AgentConfig();
        target.setAgentId(source.getAgentId());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setSystemPrompt(source.getSystemPrompt());
        target.setModelName(source.getModelName());
        target.setStreaming(source.isStreaming());
        target.setEnableThinking(source.isEnableThinking());
        target.setSkills(new ArrayList<>(source.getSkills()));
        target.setUserTools(new ArrayList<>(source.getUserTools()));
        target.setSystemTools(new ArrayList<>(source.getSystemTools()));
        target.setAutoContext(source.isAutoContext());
        target.setAutoContextMsgThreshold(source.getAutoContextMsgThreshold());
        target.setAutoContextLastKeep(source.getAutoContextLastKeep());
        target.setAutoContextTokenRatio(source.getAutoContextTokenRatio());
        target.setRagEnabled(source.isRagEnabled());
        target.setRagRetrieveLimit(source.getRagRetrieveLimit());
        target.setRagScoreThreshold(source.getRagScoreThreshold());
        target.setRagMode(source.getRagMode());
        target.setModality(source.getModality());
        target.setCategory(source.getCategory());
        target.setApprovalRequired(source.isApprovalRequired());
        target.setApprovalTools(new ArrayList<>(source.getApprovalTools()));
        target.setStructuredOutputClass(source.getStructuredOutputClass());
        target.setStructuredOutputReminder(source.getStructuredOutputReminder());
        target.setType(source.getType());
        target.setSubAgents(new ArrayList<>(source.getSubAgents()));
        target.setParallel(source.getParallel());
        target.setHandoffTriggers(new ArrayList<>(source.getHandoffTriggers()));
        target.setSamplePrompts(source.getSamplePrompts().stream()
                .map(sample -> new SamplePrompt(preview(sample.getPrompt()),
                        sample.getExpectedBehavior() != null ? sample.getExpectedBehavior() : ""))
                .toList());
        return target;
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        int maxLength = 96;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    /**
     * Get detailed information about a skill (including description).
     */
    @GetMapping("/api/skills/{skillName}")
    @ResponseBody
    public ResponseEntity<?> getSkillInfo(@PathVariable String skillName) {
        Map<String, Object> skillInfo = agentConfigService.getSkillInfo(skillName);
        if (skillInfo == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Skill not found: " + skillName));
        }
        return ResponseEntity.ok(skillInfo);
    }

    /**
     * Get detailed information about a tool (including description).
     */
    @GetMapping("/api/tools/{toolName}")
    @ResponseBody
    public ResponseEntity<?> getToolInfo(@PathVariable String toolName) {
        Map<String, Object> toolInfo = agentConfigService.getToolInfo(toolName);
        if (toolInfo == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Tool not found: " + toolName));
        }
        return ResponseEntity.ok(toolInfo);
    }

    // ---- File Endpoints ----

    /**
     * File upload endpoint.
     * Supports documents (.docx, .pdf, .xlsx), images (.jpg, .jpeg, .png, .gif, .webp), and audio (.wav, .mp3, .m4a).
     */
    @PostMapping("/chat/upload")
    @ResponseBody
    public Map<String, String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Map.of("error", "File is empty");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            return Map.of("error", "File name is missing");
        }

        String lowerName = originalName.toLowerCase();
        // Supported formats
        boolean isDocument = lowerName.endsWith(".docx") || lowerName.endsWith(".pdf") || lowerName.endsWith(".xlsx");
        boolean isImage = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                         lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                         lowerName.endsWith(".webp");
        boolean isAudio = lowerName.endsWith(".wav") || lowerName.endsWith(".mp3") ||
                         lowerName.endsWith(".m4a") || lowerName.endsWith(".mp4");

        if (!isDocument && !isImage && !isAudio) {
            return Map.of("error", "Supported formats: .docx, .pdf, .xlsx, .jpg, .jpeg, .png, .gif, .webp, .wav, .mp3, .m4a");
        }

        try {
            String fileId = UUID.randomUUID().toString();
            String ext = lowerName.substring(lowerName.lastIndexOf('.'));
            String savedName = fileId + ext;
            Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(savedName);
            file.transferTo(filePath.toFile());

            log.info("File uploaded: {} -> {}", originalName, filePath);

            return Map.of(
                    "fileId", fileId,
                    "fileName", originalName,
                    "filePath", filePath.toAbsolutePath().toString(),
                    "fileType", isImage ? "image" : isAudio ? "audio" : "document"
            );
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return Map.of("error", "Failed to save file: " + e.getMessage());
        }
    }

    /**
     * File download endpoint - serves uploaded and edited files.
     */
    @GetMapping("/chat/download")
    @ResponseBody
    public ResponseEntity<Resource> downloadFile(@RequestParam String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (fileId.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
            Path normalizedUploadDir = uploadDir.normalize().toAbsolutePath();
            Path filePath = normalizedUploadDir.resolve(fileId).normalize().toAbsolutePath();

            if (!filePath.startsWith(normalizedUploadDir)) {
                return ResponseEntity.badRequest().build();
            }

            if (!filePath.toFile().exists() && !fileId.contains(".")) {
                File[] matchingFiles = uploadDir.toFile().listFiles((dir, name) ->
                        name.startsWith(fileId) && (name.endsWith(".docx") || name.endsWith(".pdf") || name.endsWith(".xlsx"))
                );
                if (matchingFiles != null && matchingFiles.length > 0) {
                    filePath = matchingFiles[0].toPath().normalize().toAbsolutePath();
                    if (!filePath.startsWith(normalizedUploadDir)) {
                        return ResponseEntity.badRequest().build();
                    }
                }
            }

            File file = filePath.toFile();
            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            String fileName = file.getName();
            String contentType = "application/octet-stream";
            if (fileName.endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (fileName.endsWith(".pdf")) {
                contentType = "application/pdf";
            } else if (fileName.endsWith(".xlsx")) {
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }

            Resource resource = new FileSystemResource(file);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to download file: {}", fileId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
