"""Unit tests for cli-anything-agentscope core modules.

Uses mock HTTP responses — no server dependency.
"""

import json
import os
import unittest
from unittest.mock import patch, MagicMock

from cli_anything.agentscope.core import chat
from cli_anything.agentscope.core import project
from cli_anything.agentscope.utils import agentscope_backend as backend


class TestBaseURL(unittest.TestCase):
    def test_default_url(self):
        with patch.dict(os.environ, {}, clear=True):
            os.environ.pop("AGENTSCOPE_BASE_URL", None)
            self.assertEqual(backend.get_base_url(), "http://localhost:8080")

    def test_custom_url(self):
        with patch.dict(os.environ, {"AGENTSCOPE_BASE_URL": "http://myhost:9999"}):
            self.assertEqual(backend.get_base_url(), "http://myhost:9999")

    def test_custom_url_strips_trailing_slash(self):
        with patch.dict(os.environ, {"AGENTSCOPE_BASE_URL": "http://myhost:9999/"}):
            self.assertEqual(backend.get_base_url(), "http://myhost:9999")


class TestServerStatus(unittest.TestCase):
    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.get")
    def test_status_ok(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = [{"agentId": "a1"}, {"agentId": "a2"}]
        mock_resp.raise_for_status.return_value = None
        mock_get.return_value = mock_resp

        result = backend.server_status("http://test:8080")
        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["agent_count"], 2)

    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.get")
    def test_status_offline(self, mock_get):
        mock_get.side_effect = backend.requests.ConnectionError("refused")
        result = backend.server_status("http://test:8080")
        self.assertEqual(result["status"], "offline")


class TestListAgents(unittest.TestCase):
    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.get")
    def test_list_agents(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.json.return_value = [
            {"agentId": "chat-basic", "name": "Basic Chat", "type": "SINGLE"},
        ]
        mock_resp.raise_for_status.return_value = None
        mock_get.return_value = mock_resp

        agents = backend.list_agents("http://test:8080")
        self.assertEqual(len(agents), 1)
        self.assertEqual(agents[0]["agentId"], "chat-basic")


class TestGetAgent(unittest.TestCase):
    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.get")
    def test_get_agent(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {
            "agentId": "chat-basic", "name": "Basic Chat", "modelName": "qwen-plus"
        }
        mock_resp.raise_for_status.return_value = None
        mock_get.return_value = mock_resp

        a = backend.get_agent("chat-basic", "http://test:8080")
        self.assertEqual(a["agentId"], "chat-basic")
        self.assertEqual(a["modelName"], "qwen-plus")


class TestSessions(unittest.TestCase):
    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.get")
    def test_list_sessions(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.json.return_value = [{"sessionId": "abc123"}]
        mock_resp.raise_for_status.return_value = None
        mock_get.return_value = mock_resp

        sessions = backend.list_sessions("http://test:8080")
        self.assertEqual(len(sessions), 1)

    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.post")
    def test_create_session(self, mock_post):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"sessionId": "new123", "agentId": "chat-basic"}
        mock_resp.raise_for_status.return_value = None
        mock_post.return_value = mock_resp

        result = backend.create_session("chat-basic", "http://test:8080")
        self.assertEqual(result["sessionId"], "new123")

    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.delete")
    def test_delete_session(self, mock_delete):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"deleted": True}
        mock_resp.raise_for_status.return_value = None
        mock_delete.return_value = mock_resp

        result = backend.delete_session("abc123", "http://test:8080")
        self.assertTrue(result["deleted"])


class TestSendMessage(unittest.TestCase):
    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.post")
    def test_send_message_basic(self, mock_post):
        sse_lines = [
            "event:message",
            'data:{"type":"text","content":"Hello!"}',
            "event:message",
            'data:{"type":"done","content":""}',
        ]
        mock_resp = MagicMock()
        mock_resp.raise_for_status.return_value = None
        mock_resp.iter_lines.return_value = iter(sse_lines)
        mock_post.return_value = mock_resp

        events = backend.send_message("Hi", agent_id="chat-basic", base_url="http://test:8080")
        self.assertEqual(len(events), 2)
        self.assertEqual(events[0]["type"], "text")
        self.assertEqual(events[0]["content"], "Hello!")


class TestUploadFile(unittest.TestCase):
    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.post")
    def test_upload_file(self, mock_post):
        import tempfile
        mock_resp = MagicMock()
        mock_resp.json.return_value = {
            "fileId": "f1", "fileName": "test.txt", "filePath": "/tmp/test.txt", "fileType": "document"
        }
        mock_resp.raise_for_status.return_value = None
        mock_post.return_value = mock_resp

        with tempfile.NamedTemporaryFile(suffix=".txt", delete=False) as f:
            f.write(b"test content")
            f.flush()
            result = backend.upload_file(f.name, "http://test:8080")
            self.assertEqual(result["fileId"], "f1")
            os.unlink(f.name)


class TestKnowledge(unittest.TestCase):
    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.get")
    def test_list_docs(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.json.return_value = ["doc1.pdf", "doc2.txt"]
        mock_resp.raise_for_status.return_value = None
        mock_get.return_value = mock_resp

        docs = backend.list_knowledge_docs("http://test:8080")
        self.assertEqual(len(docs), 2)

    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.delete")
    def test_remove_knowledge(self, mock_delete):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"removed": True}
        mock_resp.raise_for_status.return_value = None
        mock_delete.return_value = mock_resp

        result = backend.remove_knowledge("doc1.pdf", "http://test:8080")
        self.assertTrue(result["removed"])

    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.post")
    def test_search_knowledge(self, mock_post):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {
            "query": "test", "count": 1,
            "results": [{"content": "found text", "score": "0.85"}]
        }
        mock_resp.raise_for_status.return_value = None
        mock_post.return_value = mock_resp

        result = backend.search_knowledge("test", base_url="http://test:8080")
        self.assertEqual(result["count"], 1)


class TestSkillToolInfo(unittest.TestCase):
    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.get")
    def test_get_skill_info(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"name": "docx", "type": "skill"}
        mock_resp.raise_for_status.return_value = None
        mock_get.return_value = mock_resp

        result = backend.get_skill_info("docx", "http://test:8080")
        self.assertEqual(result["name"], "docx")

    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.get")
    def test_get_tool_info(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"name": "parse_docx", "type": "tool"}
        mock_resp.raise_for_status.return_value = None
        mock_get.return_value = mock_resp

        result = backend.get_tool_info("parse_docx", "http://test:8080")
        self.assertEqual(result["name"], "parse_docx")


class TestChatExtractText(unittest.TestCase):
    def test_extract_text_empty(self):
        self.assertEqual(chat.extract_text([]), "")

    def test_extract_text_single(self):
        events = [{"type": "text", "content": "Hello!"}]
        self.assertEqual(chat.extract_text(events), "Hello!")

    def test_extract_text_multiple(self):
        events = [
            {"type": "text", "content": "Hello"},
            {"type": "text", "content": " World"},
            {"type": "done", "content": ""},
        ]
        self.assertEqual(chat.extract_text(events), "Hello World")

    def test_extract_text_no_text_events(self):
        events = [
            {"type": "agent_start", "content": {}},
            {"type": "done", "content": ""},
        ]
        self.assertEqual(chat.extract_text(events), "")


class TestChatExtractDebug(unittest.TestCase):
    def test_extract_debug(self):
        events = [
            {"type": "agent_start", "content": {}},
            {"type": "text", "content": "hi"},
            {"type": "tool_start", "content": {}},
            {"type": "done", "content": ""},
        ]
        debug = chat.extract_debug(events)
        types = [e["type"] for e in debug]
        self.assertIn("agent_start", types)
        self.assertIn("tool_start", types)
        self.assertNotIn("text", types)
        self.assertNotIn("done", types)

    def test_extract_debug_empty(self):
        self.assertEqual(chat.extract_debug([]), [])


class TestChatExtractMetrics(unittest.TestCase):
    def test_extract_metrics(self):
        events = [
            {"type": "llm_end", "content": {"inputTokens": 100, "outputTokens": 50}},
            {"type": "llm_end", "content": {"inputTokens": 200, "outputTokens": 80}},
            {"type": "tool_end", "content": {}},
            {"type": "agent_end", "content": {"duration_ms": 1500}},
        ]
        metrics = chat.extract_metrics(events)
        self.assertEqual(metrics["llm_calls"], 2)
        self.assertEqual(metrics["tool_calls"], 1)
        self.assertEqual(metrics["input_tokens"], 300)
        self.assertEqual(metrics["output_tokens"], 130)
        self.assertEqual(metrics["agent_end"]["duration_ms"], 1500)

    def test_extract_metrics_empty(self):
        metrics = chat.extract_metrics([])
        self.assertEqual(metrics["llm_calls"], 0)
        self.assertEqual(metrics["tool_calls"], 0)
        self.assertEqual(metrics["input_tokens"], 0)
        self.assertEqual(metrics["output_tokens"], 0)


class TestProjectDelegates(unittest.TestCase):
    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.get")
    def test_project_status(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.json.return_value = [{"agentId": "a1"}]
        mock_resp.raise_for_status.return_value = None
        mock_get.return_value = mock_resp

        result = project.status()
        self.assertEqual(result["status"], "ok")

    @patch("cli_anything.agentscope.utils.agentscope_backend.requests.get")
    def test_project_info(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.json.return_value = [{"agentId": "a1"}]
        mock_resp.raise_for_status.return_value = None
        mock_get.return_value = mock_resp

        result = project.info()
        self.assertIn("base_url", result)


if __name__ == "__main__":
    unittest.main()
