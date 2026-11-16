#!/usr/bin/env python3
"""Valida a classificação Gemini pelo backend, sem ler ou registrar a chave."""
import argparse
from datetime import datetime, timezone
import http.cookiejar
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


class ValidationFailure(Exception):
    pass


def request_json(client, url, label, method="GET", payload=None, headers=None, expected=200):
    request_headers = {"Accept": "application/json", **(headers or {})}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
    try:
        with client.open(request, timeout=40) as response:
            if response.status != expected:
                raise ValidationFailure(f"{label}: HTTP inesperado {response.status}")
            raw = response.read(131073)
        if len(raw) > 131072:
            raise ValidationFailure(f"{label}: resposta acima do limite")
        body = json.loads(raw)
        if not isinstance(body, dict):
            raise ValidationFailure(f"{label}: JSON deve ser um objeto")
        return body
    except urllib.error.HTTPError as error:
        # Nunca imprimir o corpo externo, cabeçalhos, cookies ou URL da ocorrência.
        raise ValidationFailure(f"{label}: HTTP {error.code}") from None
    except (urllib.error.URLError, TimeoutError, OSError):
        raise ValidationFailure(f"{label}: serviço indisponível ou timeout") from None
    except (ValueError, UnicodeError):
        raise ValidationFailure(f"{label}: JSON inválido") from None


def validate_classification(row):
    source = row.get("tipoAnalise")
    if source != "IA_GEMINI":
        if isinstance(source, str) and source in {"REGRAS", "REGRAS_CONTINGENCIA"}:
            raise ValidationFailure(
                f"O backend retornou {source}; a chamada Gemini real não foi confirmada. "
                "Verifique segredo, modelo, cota e acesso à API."
            )
        raise ValidationFailure("Origem da classificação inválida")
    if not isinstance(row.get("prioridade"), str) or row["prioridade"] not in {"BAIXA", "MEDIA", "ALTA"}:
        raise ValidationFailure("Prioridade fora do contrato")
    for field, limit in (("categoria", 70), ("justificativa", 250)):
        value = row.get(field)
        if not isinstance(value, str) or not value.strip() or len(value) > limit:
            raise ValidationFailure(f"Campo {field} inválido")
        if any(ord(char) < 32 or 127 <= ord(char) <= 159 for char in value):
            raise ValidationFailure(f"Campo {field} contém controle inválido")
    try:
        uuid.UUID(row["protocolo"])
    except (KeyError, ValueError, AttributeError, TypeError):
        raise ValidationFailure("Protocolo de criação inválido") from None


def run_validation(base_url, wait_seconds):
    parsed = urllib.parse.urlsplit(base_url)
    if (parsed.scheme not in {"http", "https"} or not parsed.hostname
            or parsed.username or parsed.password or parsed.query or parsed.fragment
            or parsed.path not in {"", "/"}):
        raise ValidationFailure("Informe uma origem HTTP/HTTPS sem credenciais, caminho ou query")
    local = parsed.hostname in {"127.0.0.1", "localhost", "::1"}
    if parsed.scheme == "http" and not local:
        raise ValidationFailure("Use HTTPS para servidores externos")
    base = base_url.rstrip("/")
    handlers = [urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())]
    if local:
        handlers.append(urllib.request.ProxyHandler({}))
    client = urllib.request.build_opener(*handlers)
    deadline = time.monotonic() + wait_seconds
    while True:
        try:
            health = request_json(client, base + "/actuator/health", "Health")
            if health.get("status") == "UP":
                break
        except ValidationFailure:
            pass
        if time.monotonic() >= deadline:
            raise ValidationFailure("O backend não ficou saudável no prazo")
        time.sleep(0.5)
    csrf = request_json(client, base + "/api/auth/csrf", "Sessão CSRF")
    if csrf.get("headerName") != "X-CSRF-TOKEN" or not isinstance(csrf.get("token"), str) or not csrf["token"]:
        raise ValidationFailure("Contrato CSRF inválido")
    created = request_json(
        client, base + "/api/ocorrencias", "Cadastro sintético", method="POST", expected=201,
        headers={csrf["headerName"]: csrf["token"]},
        payload={
            "descricao": "Cano rompido em praça pública, com água jorrando continuamente na via.",
            "endereco": "Praça pública de validação técnica",
            "bairro": "Validação sintética",
        },
    )
    validate_classification(created)
    tracked = request_json(
        client, base + "/api/ocorrencias/protocolo/" + created["protocolo"], "Consulta persistida"
    )
    validate_classification(tracked)
    for field in ("protocolo", "tipoAnalise", "prioridade", "categoria", "justificativa"):
        if tracked.get(field) != created.get(field):
            raise ValidationFailure("A consulta não preservou a classificação criada")
    if any(field in tracked for field in ("endereco", "descricao", "id", "tokenUpload")):
        raise ValidationFailure("A consulta pública expôs um campo privado")
    return {
        "result": "PASS", "realGeminiConfirmed": True, "tipoAnalise": "IA_GEMINI",
        "prioridade": created["prioridade"], "categoriaCharacters": len(created["categoria"]),
        "justificativaCharacters": len(created["justificativa"]),
        "checks": ["health", "CSRF", "cadastro 201", "IA_GEMINI", "schema", "consulta 200", "persistência", "privacidade"],
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8091")
    parser.add_argument("--wait-seconds", type=int, default=60)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    try:
        if not 0 <= args.wait_seconds <= 120:
            raise ValidationFailure("O prazo deve ser de 0 a 120 segundos")
        result = run_validation(args.base_url, args.wait_seconds)
        code = 0
    except ValidationFailure as error:
        result = {"result": "FAIL", "realGeminiConfirmed": False, "message": str(error)}
        code = 1
    result["checkedAtUtc"] = datetime.now(timezone.utc).isoformat()
    text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
        os.chmod(args.output, 0o600)
    print(text, end="")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
