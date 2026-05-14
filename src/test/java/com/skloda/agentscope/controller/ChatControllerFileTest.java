package com.skloda.agentscope.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControllerFileTest {

    @TempDir
    private Path tempDir;

    private String originalTmpDir;
    private ChatController controller;

    @BeforeEach
    void setUp() {
        originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempDir.toString());
        controller = new ChatController(null, null, null, null, null);
    }

    @AfterEach
    void tearDown() {
        System.setProperty("java.io.tmpdir", originalTmpDir);
    }

    @Test
    void uploadFileRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        Map<String, String> result = controller.uploadFile(file);

        assertEquals("File is empty", result.get("error"));
    }

    @Test
    void uploadFileRejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe",
                "application/octet-stream", new byte[] {1});

        Map<String, String> result = controller.uploadFile(file);

        assertTrue(result.get("error").contains("Supported formats"));
    }

    @Test
    void uploadFileStoresDocumentAndReturnsMetadata() {
        byte[] docxHeader = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "contract.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxHeader);

        Map<String, String> result = controller.uploadFile(file);

        assertNotNull(result.get("fileId"));
        assertEquals("contract.docx", result.get("fileName"));
        assertEquals("document", result.get("fileType"));
        assertTrue(result.get("filePath").endsWith(".docx"));
        assertTrue(Files.exists(Path.of(result.get("filePath"))));
    }

    @Test
    void uploadFileClassifiesImagesAndAudio() {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile image = new MockMultipartFile("file", "photo.png", "image/png", pngHeader);
        byte[] mp3Header = new byte[]{(byte) 0xFF, (byte) 0xFB, 0x50, 0x00};
        MockMultipartFile audio = new MockMultipartFile("file", "voice.mp3", "audio/mpeg", mp3Header);

        assertEquals("image", controller.uploadFile(image).get("fileType"));
        assertEquals("audio", controller.uploadFile(audio).get("fileType"));
    }

    @Test
    void downloadFileRejectsBlankId() {
        ResponseEntity<Resource> response = controller.downloadFile(" ");

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void downloadFileReturnsNotFoundForMissingFile() {
        ResponseEntity<Resource> response = controller.downloadFile("missing");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void downloadFileFindsMatchingDocxByFileIdWithoutExtension() throws Exception {
        Path uploadDir = tempDir.resolve("agentscope-uploads");
        Files.createDirectories(uploadDir);
        Files.write(uploadDir.resolve("abc123.docx"), new byte[] {1, 2, 3});

        ResponseEntity<Resource> response = controller.downloadFile("abc123");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"abc123.docx\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertNotNull(response.getBody());
    }

    @Test
    void downloadFileUsesPdfContentTypeForExactFileName() throws Exception {
        Path uploadDir = tempDir.resolve("agentscope-uploads");
        Files.createDirectories(uploadDir);
        Files.write(uploadDir.resolve("report.pdf"), new byte[] {1, 2, 3});

        ResponseEntity<Resource> response = controller.downloadFile("report.pdf");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
    }

    @Test
    void downloadFileRejectsPathTraversalWithDotDot() {
        ResponseEntity<Resource> response = controller.downloadFile("../../../etc/passwd");

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void downloadFileRejectsPathTraversalMixed() {
        ResponseEntity<Resource> response = controller.downloadFile("foo/../../etc/shadow");

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void downloadFileRejectsPathTraversalEncoded() {
        ResponseEntity<Resource> response = controller.downloadFile("..\\..\\windows\\system32\\config");

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void downloadFileAcceptsValidUuidFileId() throws Exception {
        Path uploadDir = tempDir.resolve("agentscope-uploads");
        Files.createDirectories(uploadDir);
        Files.write(uploadDir.resolve("550e8400-e29b-41d4-a716-446655440000.docx"), new byte[] {1, 2, 3});

        ResponseEntity<Resource> response = controller.downloadFile("550e8400-e29b-41d4-a716-446655440000.docx");

        assertEquals(200, response.getStatusCode().value());
    }
}
