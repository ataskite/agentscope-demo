package com.skloda.agentscope.mcp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages local demo MCP server processes using supergateway.
 * <p>
 * Starts two subprocesses that expose @modelcontextprotocol/server-everything
 * via SSE and StreamableHTTP transports using the supergateway bridge.
 * <p>
 * Requires Node.js and npx to be available on the system PATH.
 * Gracefully handles missing Node.js by logging a warning and skipping startup.
 */
@Component
public class McpDemoServer {

    private static final Logger log = LoggerFactory.getLogger(McpDemoServer.class);

    @Value("${agentscope.mcp.demo.enabled:true}")
    private boolean demoEnabled;

    @Value("${agentscope.mcp.demo.sse-port:9090}")
    private int ssePort;

    @Value("${agentscope.mcp.demo.http-port:9091}")
    private int httpPort;

    @Value("${agentscope.mcp.demo.startup-timeout:30}")
    private int startupTimeoutSeconds;

    private final List<Process> processes = new ArrayList<>();
    private volatile boolean started = false;

    @PostConstruct
    public void start() {
        if (!demoEnabled) {
            log.info("MCP demo server is disabled (agentscope.mcp.demo.enabled=false)");
            return;
        }

        if (!isNodeAvailable()) {
            log.warn("Node.js/npx not found on PATH — skipping MCP demo server startup. " +
                    "Install Node.js to enable local MCP demo servers.");
            return;
        }

        try {
            startSseServer();
            startHttpServer();

            if (waitForPorts()) {
                started = true;
                log.info("MCP demo servers started — SSE: http://localhost:{}/sse, HTTP: http://localhost:{}/mcp",
                        ssePort, httpPort);
            } else {
                log.warn("MCP demo servers did not become ready within {}s timeout", startupTimeoutSeconds);
                stopAll();
            }
        } catch (Exception e) {
            log.error("Failed to start MCP demo servers: {}", e.getMessage(), e);
            stopAll();
        }
    }

    @PreDestroy
    public void stop() {
        stopAll();
    }

    public boolean isSsePortReady() {
        return isPortOpen("localhost", ssePort);
    }

    public boolean isHttpPortReady() {
        return isPortOpen("localhost", httpPort);
    }

    public String getSseUrl() {
        return "http://localhost:" + ssePort + "/sse";
    }

    public String getHttpUrl() {
        return "http://localhost:" + httpPort + "/mcp";
    }

    public boolean isStarted() {
        return started;
    }

    // ==================== Internal ====================

    private boolean isNodeAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            p.destroyForcibly();
            if (exitCode == 0) {
                log.debug("Node.js is available");
                return true;
            }
        } catch (IOException | InterruptedException e) {
            // Not available
        }
        return false;
    }

    private void startSseServer() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "npx", "-y", "supergateway",
                "--stdio", "npx -y @modelcontextprotocol/server-everything",
                "--port", String.valueOf(ssePort),
                "--transport", "sse"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        processes.add(process);
        logProcessOutput(process, "SSE-demo");
        log.info("Started SSE demo server process on port {}", ssePort);
    }

    private void startHttpServer() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "npx", "-y", "supergateway",
                "--stdio", "npx -y @modelcontextprotocol/server-everything",
                "--port", String.valueOf(httpPort),
                "--transport", "streamable-http"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        processes.add(process);
        logProcessOutput(process, "HTTP-demo");
        log.info("Started HTTP demo server process on port {}", httpPort);
    }

    private boolean waitForPorts() {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(startupTimeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen("localhost", ssePort) && isPortOpen("localhost", httpPort)) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void logProcessOutput(Process process, String label) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[{}] {}", label, line);
                }
            } catch (IOException e) {
                // Stream closed on process exit — expected
            }
        }, "mcp-demo-" + label + "-logger");
        t.setDaemon(true);
        t.start();
    }

    private void stopAll() {
        for (Process process : processes) {
            if (process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
        }
        processes.clear();
        started = false;
        log.info("MCP demo servers stopped");
    }
}
