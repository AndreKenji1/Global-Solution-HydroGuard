#!/usr/bin/env python3
"""Consulta somente nomes de modelos; nunca imprime a chave ou a resposta bruta."""
import argparse
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    result = {"catalogOnly": True, "realGeminiConfirmed": False}
    code = 1
    if not key:
        result.update(result="MISSING_KEY")
    elif not re.fullmatch(r"gemini-[A-Za-z0-9._-]{1,90}", args.model):
        result.update(result="INVALID_MODEL_NAME")
    else:
        models = set()
        token = None
        client = urllib.request.build_opener(NoRedirect())
        try:
            for _ in range(10):
                query = {"pageSize": "1000"}
                if token:
                    query["pageToken"] = token
                request = urllib.request.Request(
                    "https://generativelanguage.googleapis.com/v1beta/models?" + urllib.parse.urlencode(query),
                    headers={"x-goog-api-key": key, "Accept": "application/json"},
                )
                with client.open(request, timeout=20) as response:
                    content = response.read(1048577)
                if len(content) > 1048576:
                    raise ValueError("limit")
                data = json.loads(content)
                for model in data.get("models", []):
                    name = model.get("name", "")
                    if (isinstance(name, str) and re.fullmatch(r"models/gemini-[A-Za-z0-9._-]{1,90}", name)
                            and "generateContent" in model.get("supportedGenerationMethods", [])):
                        models.add(name.removeprefix("models/"))
                token = data.get("nextPageToken")
                if not token:
                    break
                if not isinstance(token, str) or len(token) > 4096:
                    raise ValueError("pagination")
            else:
                raise ValueError("pagination limit")
            available = args.model in models
            result.update(result="MODEL_AVAILABLE" if available else "MODEL_UNAVAILABLE",
                          providerHttpStatus=200, selectedModel=args.model,
                          selectedModelAvailable=available, availableModels=sorted(models))
            code = 0 if available else 1
        except urllib.error.HTTPError as error:
            result.update(result="CATALOG_REQUEST_FAILED", providerHttpStatus=error.code)
        except (urllib.error.URLError, OSError, ValueError, TypeError, AttributeError):
            result.update(result="CATALOG_UNAVAILABLE_OR_INVALID")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    args.output.write_text(text, encoding="utf-8")
    os.chmod(args.output, 0o600)
    print(text, end="")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
