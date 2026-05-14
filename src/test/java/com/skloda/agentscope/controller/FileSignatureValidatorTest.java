package com.skloda.agentscope.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileSignatureValidatorTest {

    private final ChatController controller = new ChatController(null, null, null, null, null);

    @Test
    void validPdfSignature() {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};
        assertTrue(controller.isValidFileSignature(".pdf", "application/pdf", pdfHeader));
    }

    @Test
    void invalidPdfSignature() {
        byte[] fakeHeader = new byte[]{0x49, 0x44, 0x33};
        assertFalse(controller.isValidFileSignature(".pdf", "application/pdf", fakeHeader));
    }

    @Test
    void validDocxSignature() {
        byte[] zipHeader = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
        assertTrue(controller.isValidFileSignature(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", zipHeader));
        assertTrue(controller.isValidFileSignature(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", zipHeader));
    }

    @Test
    void validJpegSignature() {
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        assertTrue(controller.isValidFileSignature(".jpg", "image/jpeg", jpegHeader));
        assertTrue(controller.isValidFileSignature(".jpeg", "image/jpeg", jpegHeader));
    }

    @Test
    void validPngSignature() {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A};
        assertTrue(controller.isValidFileSignature(".png", "image/png", pngHeader));
    }

    @Test
    void validGifSignature() {
        byte[] gifHeader = "GIF89a".getBytes();
        assertTrue(controller.isValidFileSignature(".gif", "image/gif", gifHeader));
    }

    @Test
    void validMp3Signature_id3() {
        byte[] id3Header = "ID3".getBytes();
        assertTrue(controller.isValidFileSignature(".mp3", "audio/mpeg", id3Header));
    }

    @Test
    void validMp3Header_sync() {
        byte[] syncHeader = new byte[]{(byte) 0xFF, (byte) 0xFB, 0x50, 0x00};
        assertTrue(controller.isValidFileSignature(".mp3", "audio/mpeg", syncHeader));
    }

    @Test
    void validWavSignature() {
        byte[] riffHeader = "RIFF".getBytes();
        assertTrue(controller.isValidFileSignature(".wav", "audio/wav", riffHeader));
    }

    @Test
    void validMp4Signature() {
        byte[] mp4Header = new byte[12];
        System.arraycopy(new byte[]{0x00, 0x00, 0x00, 0x20}, 0, mp4Header, 0, 4);
        System.arraycopy("ftyp".getBytes(), 0, mp4Header, 4, 4);
        assertTrue(controller.isValidFileSignature(".mp4", "video/mp4", mp4Header));
        assertTrue(controller.isValidFileSignature(".m4a", "audio/mp4", mp4Header));
    }

    @Test
    void unknownExtensionPasses() {
        byte[] arbitrary = new byte[]{0x01, 0x02};
        assertTrue(controller.isValidFileSignature(".xyz", "application/octet-stream", arbitrary));
    }

    @Test
    void nullOrEmptyHeaderFails() {
        assertFalse(controller.isValidFileSignature(".pdf", "application/pdf", null));
        assertFalse(controller.isValidFileSignature(".pdf", "application/pdf", new byte[0]));
        assertFalse(controller.isValidFileSignature(".pdf", "application/pdf", new byte[1]));
    }
}
