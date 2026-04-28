"""Session management."""

from cli_anything.agentscope.utils.agentscope_backend import (
    list_sessions, create_session, delete_session,
)


def list_all(base_url: str | None = None) -> list[dict]:
    return list_sessions(base_url)


def create(agent_id: str, base_url: str | None = None) -> dict:
    return create_session(agent_id, base_url)


def remove(session_id: str, base_url: str | None = None) -> dict:
    return delete_session(session_id, base_url)
