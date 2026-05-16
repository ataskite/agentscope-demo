package com.skloda.agentscope.harness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void initializesMainWorkspace_withAgentsMd() throws Exception {
        Path workspace = tempDir.resolve("complaint-reviewer");
        WorkspaceInitializer.initializeFromTemplates("complaint-reviewer", workspace);

        assertTrue(Files.exists(workspace.resolve("AGENTS.md")));
        assertTrue(Files.exists(workspace.resolve("knowledge")));
        assertTrue(Files.exists(workspace.resolve("subagents")));
    }

    @Test
    void initializesMainWorkspace_doesNotOverwriteExisting() throws Exception {
        Path workspace = tempDir.resolve("complaint-reviewer");
        Files.createDirectories(workspace);
        String customContent = "# My custom agent";
        Files.writeString(workspace.resolve("AGENTS.md"), customContent);

        WorkspaceInitializer.initializeFromTemplates("complaint-reviewer", workspace);

        assertEquals(customContent, Files.readString(workspace.resolve("AGENTS.md")));
    }

    @Test
    void initializesSubagentWorkspace() throws Exception {
        Path subWorkspace = tempDir.resolve("root-cause-analyst");
        WorkspaceInitializer.initializeSubagentWorkspace("root-cause-analyst", subWorkspace);

        assertTrue(Files.exists(subWorkspace.resolve("AGENTS.md")));
        assertTrue(Files.exists(subWorkspace.resolve("skills")));
        assertTrue(Files.exists(subWorkspace.resolve("knowledge")));
    }

    @Test
    void initializeCreatesDirectoriesForMissingTemplates() throws Exception {
        Path workspace = tempDir.resolve("nonexistent-agent");
        assertDoesNotThrow(() ->
                WorkspaceInitializer.initializeFromTemplates("nonexistent-agent", workspace));
        assertTrue(Files.exists(workspace));
    }
}
