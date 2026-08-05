package com.skloda.agentscope.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFrontendApprovalLayoutTest {

    @Test
    void approvalResumeReplyIsAnchoredAfterApprovalCard() throws Exception {
        String chatJs = Files.readString(Path.of("src/main/resources/static/scripts/chat.js"));

        assertTrue(chatJs.contains("addAgentBubbleAfter(card)"),
                "approval resume reply should be inserted after its approval card");
    }

    @Test
    void chatPageUsesCurrentStaticAssetVersion() throws Exception {
        String chatHtml = Files.readString(Path.of("src/main/resources/templates/chat.html"));

        // Asset versions are bumped together whenever the trace/2.0-event UI changes.
        assertTrue(chatHtml.contains("/scripts/chat.js?v=2.12"),
                "chat.html should bump the script version so browsers stop using the stale trace UI");
        assertTrue(chatHtml.contains("/styles/chat.css?v=2.9"),
                "chat.html should bump the stylesheet version so browsers stop using stale approval card styles");
        assertTrue(chatHtml.contains("rel=\"icon\" href=\"data:,\""),
                "chat.html should avoid a noisy /favicon.ico 404 in the browser console");
    }

    @Test
    void stateModuleUsesSharedVersionAndIdempotentWindowProperties() throws Exception {
        String chatJs = Files.readString(Path.of("src/main/resources/static/scripts/chat.js"));
        String agentsJs = Files.readString(Path.of("src/main/resources/static/scripts/modules/agents.js"));
        String stateJs = Files.readString(Path.of("src/main/resources/static/scripts/state.js"));

        assertTrue(chatJs.contains("./state.js?v=2.4"),
                "chat.js should import the versioned state module");
        assertTrue(agentsJs.contains("../state.js?v=2.4"),
                "agents.js should import the same versioned state module");
        assertTrue(stateJs.contains("window.__agentScopeState"),
                "state.js should keep duplicate module loads on a shared state object");
        assertTrue(stateJs.contains("defineWindowStateProperty"),
                "state.js should define window properties idempotently");
    }

    @Test
    void approvalCardShowsRequestHeaderAndFinalState() throws Exception {
        String chatJs = Files.readString(Path.of("src/main/resources/static/scripts/chat.js"));
        String chatCss = Files.readString(Path.of("src/main/resources/static/styles/modules/chat.css"));

        assertTrue(chatJs.contains("请求人工审批"),
                "approval card title should read 请求人工审批");
        assertTrue(chatJs.contains("approval-collapsed"),
                "approval card should collapse after a decision");
        assertTrue(chatJs.contains("toggleApprovalCard"),
                "approval card header should toggle collapsed approval details after a decision");
        assertTrue(chatJs.contains("aria-expanded"),
                "approval card should expose expanded state for the clickable header");
        assertTrue(chatJs.contains("已批准，正在继续执行"),
                "approval card should show approved state while resuming");
        assertTrue(chatJs.contains("已批准"),
                "approval card should show a final approved state after resume completes");
        assertTrue(chatCss.contains(".approval-header-text"),
                "approval header should use a dedicated header-style text class");
        assertTrue(chatCss.contains(".approval-card-wrapper.approval-collapsed .approval-toggle"),
                "collapsed approval cards should show a rotated disclosure indicator");
        assertTrue(chatCss.contains(".approval-approved .approval-status"),
                "approved state should be styled visibly");
    }

    @Test
    void traceToolRowsUseToolResultEndForFinalStatus() throws Exception {
        String chatJs = Files.readString(Path.of("src/main/resources/static/scripts/chat.js"));

        assertTrue(chatJs.contains("case 'tool_result_end':"),
                "tool/MCP trace rows should wait for tool_result_end before deciding success or failure");
        assertTrue(chatJs.contains("payload.state === 'SUCCESS'"),
                "tool_result_end SUCCESS should mark the trace row as ok instead of relying on missing duration");
    }

    @Test
    void agentEndStopsTraceCardRunningAnimation() throws Exception {
        String chatJs = Files.readString(Path.of("src/main/resources/static/scripts/chat.js"));
        String debugJs = Files.readString(Path.of("src/main/resources/static/scripts/modules/debug.js"));
        String debugCss = Files.readString(Path.of("src/main/resources/static/styles/modules/debug.css"));

        assertTrue(chatJs.contains("completeRoundTrace(targetRound, 'success')"),
                "agent_end should stop the trace card running animation even before the terminal done event");
        assertTrue(debugJs.contains("export function completeRoundTrace"),
                "debug.js should expose a helper that completes the visual trace without clearing currentRound");
        assertTrue(debugCss.contains(".round-card.running::after"),
                "running trace cards should keep the sweep animation scoped to the running class");
    }
}
