package com.msxf.agentscope.controller;

import com.msxf.agentscope.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Autowired
    private AgentService agentService;

    @GetMapping("/")
    public String chat() {
        return "chat";
    }

    @PostMapping("/chat/send")
    @ResponseBody
    public Map<String, String> sendMessage(@RequestBody Map<String, String> request) {
        String agentType = request.getOrDefault("agentType", "basic");
        String message = request.get("message");
        String filePath = request.get("filePath");
        String fileName = request.get("fileName");

        if (message == null || message.isBlank()) {
            return Map.of("error", "Message cannot be empty");
        }

        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout

        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            log.warn("SSE emitter timed out for session: {}", sessionId);
        });
        emitter.onError(ex -> {
            emitters.remove(sessionId);
            log.error("SSE emitter error for session: {}", sessionId, ex);
        });

        executor.submit(() -> {
            try {
                agentService.streamToEmitter(agentType, message, filePath, fileName, emitter);
            } catch (Exception e) {
                log.error("Error during agent streaming", e);
                emitter.completeWithError(e);
            }
        });

        return Map.of("sessionId", sessionId);
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter stream(@RequestParam String sessionId) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            SseEmitter errorEmitter = new SseEmitter();
            executor.submit(() -> {
                try {
                    errorEmitter.send(SseEmitter.event().name("message").data("{\"type\":\"error\",\"message\":\"Session not found\"}"));
                    errorEmitter.complete();
                } catch (Exception e) {
                    // ignore
                }
            });
            return errorEmitter;
        }
        return emitter;
    }

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
        if (!lowerName.endsWith(".docx") && !lowerName.endsWith(".pdf")) {
            return Map.of("error", "Only .docx and .pdf files are supported");
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
}
