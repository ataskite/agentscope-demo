# TEST.md — cli-anything-agentscope

## Test Inventory Plan

| File | Type | Planned Count |
|------|------|---------------|
| `test_core.py` | Unit tests (synthetic, no server) | ~40 |
| `test_full_e2e.py` | E2E tests (requires running server) | ~20 |

## Unit Test Plan

### `utils/agentscope_backend.py` (mock HTTP)
- `test_get_base_url_default` — default URL from env
- `test_get_base_url_custom` — custom URL from env var
- `test_get_base_url_custom_strips_slash` — trailing slash stripped
- `test_server_status_ok` — mock 200 response
- `test_server_status_offline` — mock connection error
- `test_list_agents` — mock agent list
- `test_get_agent` — mock single agent
- `test_list_sessions` — mock session list
- `test_create_session` — mock session creation
- `test_delete_session` — mock session deletion
- `test_send_message_basic` — mock SSE stream
- `test_upload_file` — mock file upload
- `test_list_knowledge_docs` — mock doc list
- `test_remove_knowledge` — mock knowledge removal
- `test_search_knowledge` — mock knowledge search
- `test_get_skill_info` — mock skill info
- `test_get_tool_info` — mock tool info
- `test_get_agent_messages` — mock agent messages endpoint
- `test_get_sample_prompt` — mock sample prompt endpoint
- `test_approve_message` — mock approve SSE stream
- `test_reject_message_with_reason` — mock reject with reason
- `test_knowledge_status` — mock knowledge status

### `core/chat.py` (pure functions)
- `test_extract_text_empty` — empty events
- `test_extract_text_basic` — single text event
- `test_extract_text_multiple` — multiple text events
- `test_extract_text_no_text_events` — no text events
- `test_extract_debug` — filters debug events
- `test_extract_debug_empty` — empty events
- `test_extract_debug_multi_agent_events` — new P6 event types (loop, graph, roundtable, task, approval)
- `test_extract_metrics` — aggregates token counts
- `test_extract_metrics_empty` — no events

### `core/project.py` (delegates to backend)
- `test_status` — wraps server_status
- `test_info` — wraps with base_url

## E2E Test Plan

### Server connectivity
- `test_server_status` — real server health check

### Agent operations
- `test_list_agents` — list all agents, verify structure
- `test_get_agent` — get specific agent, verify fields
- `test_agent_messages` — get chat history for an agent
- `test_agent_sample_prompt` — get sample prompt by index

### Session operations
- `test_session_lifecycle` — create → list → delete
- `test_session_list` — list sessions without error

### Chat operations
- `test_chat_send_text` — send text message, get response
- `test_chat_send_streaming` — stream a message, verify text events
- `test_chat_send_with_session` — create session, send within session

### Knowledge operations
- `test_knowledge_list` — list docs (may be empty)
- `test_knowledge_upload_and_search` — upload doc, search, remove
- `test_knowledge_status` — check indexing status

### File operations
- `test_file_upload` — upload a test file

### CLI subprocess tests
- `test_cli_help` — `--help` returns 0
- `test_cli_server_status_json` — `--json server status`
- `test_cli_agent_list_json` — `--json agent list`

### Realistic Workflow Scenarios

**Workflow 1: Complete chat session**
1. Create session with chat-basic agent
2. Send "Hello" message
3. Send follow-up message in same session
4. List sessions, verify message count
5. Delete session

**Workflow 2: Document analysis pipeline**
1. Upload a test text file
2. Send message referencing the file
3. Verify response contains text events

**Workflow 3: Knowledge base Q&A**
1. Upload a text document to knowledge base
2. Search for content
3. Verify search returns results
4. Remove document
5. Verify removal

---

## Test Results

### Unit Tests (test_core.py)

```
cli_anything/agentscope/tests/test_core.py::TestBaseURL::test_custom_url PASSED
cli_anything/agentscope/tests/test_core.py::TestBaseURL::test_custom_url_strips_trailing_slash PASSED
cli_anything/agentscope/tests/test_core.py::TestBaseURL::test_default_url PASSED
cli_anything/agentscope/tests/test_core.py::TestServerStatus::test_status_offline PASSED
cli_anything/agentscope/tests/test_core.py::TestServerStatus::test_status_ok PASSED
cli_anything/agentscope/tests/test_core.py::TestListAgents::test_list_agents PASSED
cli_anything/agentscope/tests/test_core.py::TestGetAgent::test_get_agent PASSED
cli_anything/agentscope/tests/test_core.py::TestSessions::test_create_session PASSED
cli_anything/agentscope/tests/test_core.py::TestSessions::test_delete_session PASSED
cli_anything/agentscope/tests/test_core.py::TestSessions::test_list_sessions PASSED
cli_anything/agentscope/tests/test_core.py::TestSendMessage::test_send_message_basic PASSED
cli_anything/agentscope/tests/test_core.py::TestUploadFile::test_upload_file PASSED
cli_anything/agentscope/tests/test_core.py::TestKnowledge::test_list_docs PASSED
cli_anything/agentscope/tests/test_core.py::TestKnowledge::test_remove_knowledge PASSED
cli_anything/agentscope/tests/test_core.py::TestKnowledge::test_search_knowledge PASSED
cli_anything/agentscope/tests/test_core.py::TestSkillToolInfo::test_get_skill_info PASSED
cli_anything/agentscope/tests/test_core.py::TestSkillToolInfo::test_get_tool_info PASSED
cli_anything/agentscope/tests/test_core.py::TestAgentMessages::test_get_agent_messages PASSED
cli_anything/agentscope/tests/test_core.py::TestAgentMessages::test_get_sample_prompt PASSED
cli_anything/agentscope/tests/test_core.py::TestApproveMessage::test_approve_message PASSED
cli_anything/agentscope/tests/test_core.py::TestApproveMessage::test_reject_message_with_reason PASSED
cli_anything/agentscope/tests/test_core.py::TestKnowledgeStatus::test_knowledge_status PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractText::test_extract_text_empty PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractText::test_extract_text_multiple PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractText::test_extract_text_no_text_events PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractText::test_extract_text_single PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractDebug::test_extract_debug PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractDebug::test_extract_debug_empty PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractDebug::test_extract_debug_multi_agent_events PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractMetrics::test_extract_metrics PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractMetrics::test_extract_metrics_empty PASSED
cli_anything/agentscope/tests/test_core.py::TestProjectDelegates::test_project_info PASSED
cli_anything/agentscope/tests/test_core.py::TestProjectDelegates::test_project_status PASSED

33 passed in 0.14s
```

### E2E Tests (test_full_e2e.py)

*(E2E tests require a running AgentScope Demo server. Start with `mvn spring-boot:run`)*

### Summary

| Category | Total | Passed | Status |
|----------|-------|--------|--------|
| Unit tests | 33 | 33 | All pass |
| E2E tests | ~15 | — | Requires server |

### Coverage Notes

- All HTTP backend functions have mock-based unit tests (22 backend tests)
- All chat event extraction functions have pure unit tests (9 chat tests)
- New endpoint coverage: agent messages, sample prompts, approve/reject, knowledge status
- Multi-agent debug event types (loop, graph, roundtable, task, approval) covered
- E2E tests cover full server workflow but require a running server
- CLI subprocess tests verify installed command works via `_resolve_cli()`
