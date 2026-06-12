package com.skloda.agentscope.harness;

import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Component
public class FilesystemSpecFactory {

    public LocalFilesystemSpec createLocal() {
        return new LocalFilesystemSpec()
                .mode(LocalFsMode.SANDBOXED)
                .project(Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-sandbox"))
                .executeTimeoutSeconds(120)
                .maxOutputBytes(10000000)
                .inheritEnv(true);
    }

    public LocalFilesystemSpec createLocalUnrestricted() {
        return new LocalFilesystemSpec()
                .mode(LocalFsMode.UNRESTRICTED)
                .project(Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-workspace"))
                .executeTimeoutSeconds(300)
                .maxOutputBytes(50000000);
    }

    public DockerFilesystemSpec createDocker() {
        return new DockerFilesystemSpec()
                .client(new DockerSandboxClient())
                .image("python:3.11-slim")
                .workspaceRoot("/workspace")
                .memorySizeBytes(2_000_000_000L)
                .cpuCount(2L)
                .environment(Map.of(
                        "PYTHONUNBUFFERED", "1",
                        "TZ", "Asia/Shanghai"
                ))
                .snapshotSpec(new NoopSnapshotSpec());
    }
}
