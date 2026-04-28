"""Knowledge base management."""

from cli_anything.agentscope.utils.agentscope_backend import (
    list_knowledge_docs, upload_knowledge, remove_knowledge, search_knowledge,
)


def list_docs(base_url: str | None = None) -> list[str]:
    return list_knowledge_docs(base_url)


def upload(file_path: str, base_url: str | None = None) -> dict:
    return upload_knowledge(file_path, base_url)


def remove(file_name: str, base_url: str | None = None) -> dict:
    return remove_knowledge(file_name, base_url)


def search(
    query: str,
    limit: int = 3,
    threshold: float = 0.5,
    base_url: str | None = None,
) -> dict:
    return search_knowledge(query, limit, threshold, base_url)
