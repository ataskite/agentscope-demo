package com.skloda.agentscope.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P1-2 security fix: upload directory whitelist for every client-supplied file path.
 *
 * <p>All paths received from the request body ({@code filePath}, {@code images[].path},
 * {@code audio.path}) and all {@code filePath} parameters accepted by document parsing
 * tools must resolve to a regular file inside {@code {java.io.tmpdir}/agentscope-uploads}.
 * Anything else is rejected, closing the arbitrary-file-read surface (previously any
 * absolute path such as {@code application.yml} could be read into the LLM context).</p>
 *
 * <p>A registration table (fileId → absolute path) records files saved by
 * {@code POST /chat/upload}; it is kept for future ID-only access and audit purposes.
 * The whitelist check itself does not depend on registration, so existing clients that
 * pass back the absolute path returned by the upload endpoint keep working.</p>
 */
public final class UploadPathGuard {

    private static final Path UPLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-uploads")
            .normalize().toAbsolutePath();

    /** Registered upload files (absolute paths), maintained for audit and future ID-based access. */
    private static final Set<String> REGISTERED_FILES = ConcurrentHashMap.newKeySet();

    private UploadPathGuard() {
    }

    public static Path uploadDir() {
        return UPLOAD_DIR;
    }

    /** Record a freshly uploaded file (called by ChatController.uploadFile). */
    public static void register(Path savedPath) {
        if (savedPath != null) {
            REGISTERED_FILES.add(savedPath.normalize().toAbsolutePath().toString());
        }
    }

    public static int registeredFileCount() {
        return REGISTERED_FILES.size();
    }

    /**
     * Validate a client-supplied path against the upload whitelist.
     *
     * @return the canonical absolute path, or {@code null} for blank input
     * @throws IllegalArgumentException when the path escapes the whitelist or does not
     *                                  resolve to an existing regular file
     */
    public static Path validate(String clientPath) {
        if (clientPath == null || clientPath.isBlank()) {
            return null;
        }
        final Path resolved;
        try {
            resolved = Paths.get(clientPath).normalize().toAbsolutePath();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid file path");
        }
        if (!resolved.startsWith(UPLOAD_DIR)) {
            throw new IllegalArgumentException(
                    "File path is outside the allowed upload directory: " + UPLOAD_DIR);
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("File not found in the upload directory");
        }
        return resolved;
    }

    /**
     * Non-throwing variant for tool entry points: {@code true} only when the path is
     * inside the whitelist. Existence is deliberately NOT checked here — a missing
     * file inside the whitelist is not a security threat, and the tool's own
     * "file not found" handling stays reachable and informative.
     */
    public static boolean isAllowed(String clientPath) {
        if (clientPath == null || clientPath.isBlank()) {
            return false;
        }
        try {
            return Paths.get(clientPath).normalize().toAbsolutePath().startsWith(UPLOAD_DIR);
        } catch (Exception e) {
            return false;
        }
    }
}
