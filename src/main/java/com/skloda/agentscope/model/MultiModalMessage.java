package com.skloda.agentscope.model;

import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.AudioBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Base64Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Builder for creating multi-modal messages (text + images/audio).
 */
@Component
public class MultiModalMessage {

    private static final Logger log = LoggerFactory.getLogger(MultiModalMessage.class);

    /**
     * Create a multi-modal message with text and image.
     */
    public static Msg withImage(String text, String imagePath, String originalFileName) {
        try {
            byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            String mediaType = getMediaType(originalFileName);

            List<ContentBlock> content = new ArrayList<>();
            if (text != null && !text.isBlank()) {
                content.add(TextBlock.builder().text(text).build());
            }
            content.add(ImageBlock.builder()
                    .source(Base64Source.builder()
                            .data(base64Image)
                            .mediaType(mediaType)
                            .build())
                    .build());

            return Msg.builder()
                    .role(MsgRole.USER)
                    .content(content)
                    .build();

        } catch (Exception e) {
            log.error("Failed to create image message: {}", imagePath, e);
            // Fallback to text-only message with error info
            return Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(text + "\n\n[图片加载失败: " + originalFileName + "]")
                    .build();
        }
    }

    /**
     * Create a multi-modal message with text and audio.
     */
    public static Msg withAudio(String audioPath, String originalFileName) {
        try {
            byte[] audioBytes = Files.readAllBytes(Path.of(audioPath));
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

            String mediaType = getMediaType(originalFileName);

            return Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(
                            AudioBlock.builder()
                                    .source(Base64Source.builder()
                                            .data(base64Audio)
                                            .mediaType(mediaType)
                                            .build())
                                    .build()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Failed to create audio message: {}", audioPath, e);
            // Fallback to text message
            return Msg.builder()
                    .role(MsgRole.USER)
                    .textContent("[音频加载失败: " + originalFileName + "]")
                    .build();
        }
    }

    /**
     * Create a multi-modal message with text and multiple images.
     */
    public static Msg withMultipleImages(String text, List<ImageFile> images) {
        try {
            List<ContentBlock> content = new ArrayList<>();

            if (text != null && !text.isBlank()) {
                content.add(TextBlock.builder().text(text).build());
            }

            for (ImageFile img : images) {
                byte[] imageBytes = Files.readAllBytes(Path.of(img.path));
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                String mediaType = getMediaType(img.fileName);

                content.add(ImageBlock.builder()
                        .source(Base64Source.builder()
                                .data(base64Image)
                                .mediaType(mediaType)
                                .build())
                        .build());
            }

            return Msg.builder()
                    .role(MsgRole.USER)
                    .content(content)
                    .build();

        } catch (Exception e) {
            log.error("Failed to create multi-image message", e);
            return Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(text + "\n\n[图片加载失败]")
                    .build();
        }
    }

    /**
     * Get media type from file name.
     */
    private static String getMediaType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".png")) {
            return "image/png";
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerName.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerName.endsWith(".wav")) {
            return "audio/wav";
        } else if (lowerName.endsWith(".mp3")) {
            return "audio/mp3";
        } else if (lowerName.endsWith(".m4a")) {
            return "audio/m4a";
        } else if (lowerName.endsWith(".mp4")) {
            return "audio/mp4";
        } else {
            return "image/jpeg"; // Default fallback
        }
    }

    /**
     * Image file container.
     */
    public static class ImageFile {
        public final String path;
        public final String fileName;

        public ImageFile(String path, String fileName) {
            this.path = path;
            this.fileName = fileName;
        }
    }
}
