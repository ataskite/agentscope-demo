package com.msxf.agentscope.controller;

import com.msxf.agentscope.agent.AgentConfig;
import com.msxf.agentscope.agent.AgentConfigService;
import com.msxf.agentscope.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat Controller with SSE streaming support.
 * Uses Spring's TaskExecutor for async streaming so that send endpoints return immediately.
 */
@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentConfigService agentConfigService;

    @Autowired
    private TaskExecutor taskExecutor;

    @GetMapping("/")
    public String chat() {
        return "chat";
    }

    /**
     * Send message endpoint.
     * Submits streaming to TaskExecutor so this method returns sessionId immediately.
     */
    @PostMapping("/chat/send")
    @ResponseBody
    public Map<String, String> sendMessage(@RequestBody Map<String, String> request) {
        String agentId = request.getOrDefault("agentId", "chat.basic");
        String message = request.get("message");
        String filePath = request.get("filePath");
        String fileName = request.get("fileName");

        if (message == null || message.isBlank()) {
            return Map.of("error", "Message cannot be empty");
        }

        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout

        emitters.put(sessionId, emitter);

        // Cleanup on completion/timeout/error
        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            log.warn("SSE emitter timed out for session: {}", sessionId);
        });
        emitter.onError(ex -> {
            emitters.remove(sessionId);
            log.error("SSE emitter error for session: {}", sessionId, ex);
        });

        // Submit streaming task to TaskExecutor — returns sessionId immediately
        taskExecutor.execute(() -> {
            try {
                log.debug("[{}] Starting stream", sessionId);
                agentService.streamToEmitter(agentId, message, filePath, fileName, emitter);
            } catch (Exception e) {
                log.error("[{}] Error during agent streaming", sessionId, e);
                emitter.completeWithError(e);
            }
        });

        return Map.of("sessionId", sessionId);
    }

    /**
     * SSE stream endpoint - returns the emitter created in sendMessage.
     * The client polls this endpoint after getting sessionId.
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter stream(@RequestParam String sessionId) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            SseEmitter errorEmitter = new SseEmitter();
            taskExecutor.execute(() -> {
                try {
                    errorEmitter.send(SseEmitter.event()
                        .name("message")
                        .data("{\"type\":\"error\",\"message\":\"Session not found\"}"));
                    errorEmitter.complete();
                } catch (Exception e) {
                    log.error("[{}] Failed to send error response", sessionId, e);
                    errorEmitter.completeWithError(e);
                }
            });
            return errorEmitter;
        }
        return emitter;
    }

    /**
     * List all available agent configurations.
     */
    @GetMapping("/api/agents")
    @ResponseBody
    public List<AgentConfig> listAgents() {
        return agentConfigService.getAllAgents();
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
        return ResponseEntity.ok(config.get());
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

    /**
     * File upload endpoint.
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
        if (!lowerName.endsWith(".docx") && !lowerName.endsWith(".pdf") && !lowerName.endsWith(".xlsx")) {
            return Map.of("error", "Only .docx, .pdf, and .xlsx files are supported");
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
                "filePath", filePath.toAbsolutePath().toString()
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

        try {
            // Construct file path - supports both original uploads and edited files
            Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
            Path filePath = uploadDir.resolve(fileId);

            // If fileId is a UUID (no extension), try to find the file
            if (!filePath.toFile().exists() && !fileId.contains(".")) {
                // Try common extensions
                File[] matchingFiles = uploadDir.toFile().listFiles((dir, name) ->
                    name.startsWith(fileId) && (name.endsWith(".docx") || name.endsWith(".pdf") || name.endsWith(".xlsx"))
                );
                if (matchingFiles != null && matchingFiles.length > 0) {
                    filePath = matchingFiles[0].toPath();
                }
            }

            File file = filePath.toFile();
            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            // Determine content type
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
