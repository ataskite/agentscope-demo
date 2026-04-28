"""File upload and download."""

from cli_anything.agentscope.utils.agentscope_backend import upload_file, download_file


def upload(file_path: str, base_url: str | None = None) -> dict:
    return upload_file(file_path, base_url)


def download(file_id: str, output_path: str, base_url: str | None = None) -> str:
    return download_file(file_id, output_path, base_url)
