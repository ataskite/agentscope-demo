"""HTTP backend client for AgentScope Demo server."""

import json
import os
import sys

import requests


DEFAULT_BASE_URL = "http://localhost:8080"


def get_base_url() -> str:
    return os.environ.get("AGENTSCOPE_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


def _check_server(base_url: str | None = None) -> None:
    url = (base_url or get_base_url()) + "/api/agents"
    try:
        r = requests.get(url, timeout=5)
        r.raise_for_status()
    except requests.ConnectionError:
        raise RuntimeError(
            f"Cannot connect to AgentScope server at {url}.\n"
            "Start it with: mvn spring-boot:run"
        )
    except requests.HTTPError as e:
        raise RuntimeError(f"Server error: {e}")


def server_status(base_url: str | None = None) -> dict:
    url = (base_url or get_base_url()) + "/api/agents"
    try:
        r = requests.get(url, timeout=5)
        r.raise_for_status()
        agents = r.json()
        return {"status": "ok", "url": base_url or get_base_url(), "agent_count": len(agents)}
    except requests.ConnectionError:
        return {"status": "offline", "url": base_url or get_base_url(), "agent_count": 0}
    except Exception as e:
        return {"status": "error", "url": base_url or get_base_url(), "error": str(e)}


def list_agents(base_url: str | None = None) -> list[dict]:
    url = (base_url or get_base_url()) + "/api/agents"
    r = requests.get(url, timeout=10)
    r.raise_for_status()
    return r.json()


def get_agent(agent_id: str, base_url: str | None = None) -> dict:
    url = (base_url or get_base_url()) + f"/api/agents/{agent_id}"
    r = requests.get(url, timeout=10)
    r.raise_for_status()
    return r.json()


def list_sessions(base_url: str | None = None) -> list[dict]:
    url = (base_url or get_base_url()) + "/api/sessions"
    r = requests.get(url, timeout=10)
    r.raise_for_status()
    return r.json()


def create_session(agent_id: str, base_url: str | None = None) -> dict:
    url = (base_url or get_base_url()) + "/api/sessions"
    r = requests.post(url, json={"agentId": agent_id}, timeout=10)
    r.raise_for_status()
    return r.json()


def delete_session(session_id: str, base_url: str | None = None) -> dict:
    url = (base_url or get_base_url()) + f"/api/sessions/{session_id}"
    r = requests.delete(url, timeout=10)
    r.raise_for_status()
    return r.json()


def send_message(
    message: str,
    agent_id: str = "chat-basic",
    session_id: str | None = None,
    file_path: str | None = None,
    file_name: str | None = None,
    images: list[dict] | None = None,
    audio: dict | None = None,
    base_url: str | None = None,
    callback: callable = None,
) -> list[dict]:
    """Send a chat message and collect all SSE events.

    Args:
        callback: Optional callable invoked with each parsed event dict.
                  Used for streaming output in REPL mode.

    Returns:
        List of all SSE event dicts.
    """
    url = (base_url or get_base_url()) + "/chat/send"
    payload = {
        "agentId": agent_id,
        "message": message,
    }
    if session_id:
        payload["sessionId"] = session_id
    if file_path:
        payload["filePath"] = file_path
    if file_name:
        payload["fileName"] = file_name
    if images:
        payload["images"] = images
    if audio:
        payload["audio"] = audio

    r = requests.post(url, json=payload, stream=True, timeout=120)
    r.raise_for_status()
    r.encoding = "utf-8"

    events = []
    current_event_type = "message"

    for line in r.iter_lines(decode_unicode=True):
        if line is None:
            continue
        if line.startswith("event:"):
            current_event_type = line.split(":", 1)[1].strip()
        elif line.startswith("data:"):
            data_str = line.split(":", 1)[1].strip()
            try:
                event = json.loads(data_str)
            except json.JSONDecodeError:
                event = {"type": "raw", "content": data_str}
            event.setdefault("type", current_event_type)
            events.append(event)
            if callback:
                callback(event)
            if event.get("type") == "done":
                r.close()
                return events

    return events


def send_message_streaming(
    message: str,
    agent_id: str = "chat-basic",
    session_id: str | None = None,
    file_path: str | None = None,
    file_name: str | None = None,
    images: list[dict] | None = None,
    audio: dict | None = None,
    base_url: str | None = None,
) -> None:
    """Send a chat message and stream text events to stdout in real time."""
    def _print_event(event: dict):
        etype = event.get("type", "")
        content = event.get("content", "")
        if etype == "text":
            print(content, end="", flush=True)
        elif etype == "error":
            print(f"\nError: {content}", file=sys.stderr)
        elif etype == "done":
            pass

    events = send_message(
        message=message,
        agent_id=agent_id,
        session_id=session_id,
        file_path=file_path,
        file_name=file_name,
        images=images,
        audio=audio,
        base_url=base_url,
        callback=_print_event,
    )
    print()
    return events


def upload_file(file_path: str, base_url: str | None = None) -> dict:
    url = (base_url or get_base_url()) + "/chat/upload"
    with open(file_path, "rb") as f:
        r = requests.post(url, files={"file": f}, timeout=60)
    r.raise_for_status()
    return r.json()


def download_file(file_id: str, output_path: str, base_url: str | None = None) -> str:
    url = (base_url or get_base_url()) + f"/chat/download?fileId={file_id}"
    r = requests.get(url, stream=True, timeout=60)
    r.raise_for_status()
    with open(output_path, "wb") as f:
        for chunk in r.iter_content(chunk_size=8192):
            f.write(chunk)
    return output_path


def list_knowledge_docs(base_url: str | None = None) -> list[str]:
    url = (base_url or get_base_url()) + "/api/knowledge/documents"
    r = requests.get(url, timeout=10)
    r.raise_for_status()
    return r.json()


def upload_knowledge(file_path: str, base_url: str | None = None) -> dict:
    url = (base_url or get_base_url()) + "/api/knowledge/upload"
    with open(file_path, "rb") as f:
        r = requests.post(url, files={"file": f}, timeout=120)
    r.raise_for_status()
    return r.json()


def remove_knowledge(file_name: str, base_url: str | None = None) -> dict:
    url = (base_url or get_base_url()) + f"/api/knowledge/documents/{file_name}"
    r = requests.delete(url, timeout=10)
    r.raise_for_status()
    return r.json()


def search_knowledge(
    query: str,
    limit: int = 3,
    threshold: float = 0.5,
    base_url: str | None = None,
) -> dict:
    url = (base_url or get_base_url()) + "/api/knowledge/search"
    r = requests.post(url, json={"query": query, "limit": limit, "threshold": threshold}, timeout=30)
    r.raise_for_status()
    return r.json()


def get_skill_info(skill_name: str, base_url: str | None = None) -> dict:
    url = (base_url or get_base_url()) + f"/api/skills/{skill_name}"
    r = requests.get(url, timeout=10)
    r.raise_for_status()
    return r.json()


def get_tool_info(tool_name: str, base_url: str | None = None) -> dict:
    url = (base_url or get_base_url()) + f"/api/tools/{tool_name}"
    r = requests.get(url, timeout=10)
    r.raise_for_status()
    return r.json()
