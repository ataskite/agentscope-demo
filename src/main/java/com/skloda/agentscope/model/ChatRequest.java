package com.skloda.agentscope.model;

/**
 * Request body for POST /chat/send.
 */
public class ChatRequest {

    private String agentId = "chat.basic";
    private String message;
    private String filePath;
    private String fileName;
    private String sessionId;

    // Multi-modal support
    private java.util.List<ImageFile> images;
    private AudioFile audio;

    public String getAgentId() {
        return agentId;
    }

    public static class ImageFile {
        private String path;
        private String fileName;

        public ImageFile() {}

        public ImageFile(String path, String fileName) {
            this.path = path;
            this.fileName = fileName;
        }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }

    public static class AudioFile {
        private String path;
        private String fileName;

        public AudioFile() {}

        public AudioFile(String path, String fileName) {
            this.path = path;
            this.fileName = fileName;
        }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }

    public java.util.List<ImageFile> getImages() {
        return images;
    }

    public void setImages(java.util.List<ImageFile> images) {
        this.images = images;
    }

    public AudioFile getAudio() {
        return audio;
    }

    public void setAudio(AudioFile audio) {
        this.audio = audio;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
