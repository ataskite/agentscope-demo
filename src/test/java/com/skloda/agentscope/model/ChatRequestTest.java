package com.skloda.agentscope.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChatRequestTest {

    @Test
    void defaultAgentIdUsesLegacyFallback() {
        ChatRequest request = new ChatRequest();

        assertEquals("chat.basic", request.getAgentId());
    }

    @Test
    void storesTextFileSessionAndMediaPayloads() {
        ChatRequest request = new ChatRequest();
        ChatRequest.ImageFile image = new ChatRequest.ImageFile("/tmp/image.png", "image.png");
        ChatRequest.AudioFile audio = new ChatRequest.AudioFile("/tmp/audio.wav", "audio.wav");
        List<ChatRequest.ImageFile> images = List.of(image);

        request.setAgentId("vision-analyzer");
        request.setMessage("describe it");
        request.setFilePath("/tmp/doc.pdf");
        request.setFileName("doc.pdf");
        request.setSessionId("session-1");
        request.setImages(images);
        request.setAudio(audio);

        assertEquals("vision-analyzer", request.getAgentId());
        assertEquals("describe it", request.getMessage());
        assertEquals("/tmp/doc.pdf", request.getFilePath());
        assertEquals("doc.pdf", request.getFileName());
        assertEquals("session-1", request.getSessionId());
        assertSame(images, request.getImages());
        assertSame(audio, request.getAudio());
        assertEquals("/tmp/image.png", image.getPath());
        assertEquals("image.png", image.getFileName());
        assertEquals("/tmp/audio.wav", audio.getPath());
        assertEquals("audio.wav", audio.getFileName());
    }

    @Test
    void imageAndAudioNoArgConstructorsSupportBeanBinding() {
        ChatRequest.ImageFile image = new ChatRequest.ImageFile();
        ChatRequest.AudioFile audio = new ChatRequest.AudioFile();

        image.setPath("/tmp/a.jpg");
        image.setFileName("a.jpg");
        audio.setPath("/tmp/a.mp3");
        audio.setFileName("a.mp3");

        assertEquals("/tmp/a.jpg", image.getPath());
        assertEquals("a.jpg", image.getFileName());
        assertEquals("/tmp/a.mp3", audio.getPath());
        assertEquals("a.mp3", audio.getFileName());
    }

    @Test
    void unsetOptionalFieldsRemainNull() {
        ChatRequest request = new ChatRequest();

        assertNull(request.getMessage());
        assertNull(request.getFilePath());
        assertNull(request.getFileName());
        assertNull(request.getSessionId());
        assertNull(request.getImages());
        assertNull(request.getAudio());
    }
}
