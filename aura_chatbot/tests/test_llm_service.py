"""Unit tests for llm_service.py — Groq chat + Ollama embeddings with the
Groq→Ollama silent-failure fallback. All external calls are mocked; no real
network traffic ever leaves the test process."""

from unittest.mock import MagicMock


def test_generar_embedding_llama_a_ollama(aura_modules, monkeypatch):
    llm = aura_modules.llm_service
    fake = MagicMock(return_value={"embedding": [0.1, 0.2, 0.3]})
    monkeypatch.setattr(llm.ollama_client, "embeddings", fake)

    resultado = llm.generar_embedding("texto de prueba")

    assert resultado == [0.1, 0.2, 0.3]
    fake.assert_called_once_with(model=llm.EMBED_MODEL, prompt="texto de prueba")


def test_generar_respuesta_usa_groq_cuando_hay_key(aura_modules, monkeypatch):
    llm = aura_modules.llm_service
    monkeypatch.setattr(llm, "GROQ_API_KEY", "test-key-123")
    llamadas = {}

    class FakeResponse:
        def raise_for_status(self):
            return None

        def json(self):
            return {"choices": [{"message": {"content": "respuesta de groq"}}]}

    class FakeClient:
        def __init__(self, *args, **kwargs):
            llamadas["timeout"] = kwargs.get("timeout")

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def post(self, url, json=None, headers=None):
            llamadas["url"] = url
            llamadas["payload"] = json
            llamadas["headers"] = headers
            return FakeResponse()

    monkeypatch.setattr(llm.httpx, "Client", FakeClient)
    ollama_chat = MagicMock(return_value={"message": {"content": "no debe usarse"}})
    monkeypatch.setattr(llm.ollama_client, "chat", ollama_chat)

    resultado = llm.generar_respuesta("mi prompt")

    assert resultado == "respuesta de groq"
    assert llamadas["url"] == llm.GROQ_API_URL
    assert llamadas["payload"]["model"] == llm.GROQ_MODEL
    assert llamadas["payload"]["messages"] == [{"role": "user", "content": "mi prompt"}]
    assert llamadas["headers"]["Authorization"] == "Bearer test-key-123"
    assert llamadas["timeout"] == 30
    ollama_chat.assert_not_called()


def test_generar_respuesta_fallback_a_ollama_cuando_groq_falla(aura_modules, monkeypatch):
    llm = aura_modules.llm_service
    monkeypatch.setattr(llm, "GROQ_API_KEY", "test-key-123")

    class FailingClient:
        def __init__(self, *args, **kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def post(self, *args, **kwargs):
            raise RuntimeError("Groq caído")

    monkeypatch.setattr(llm.httpx, "Client", FailingClient)
    ollama_chat = MagicMock(return_value={"message": {"content": "respuesta de ollama"}})
    monkeypatch.setattr(llm.ollama_client, "chat", ollama_chat)

    resultado = llm.generar_respuesta("mi prompt")

    assert resultado == "respuesta de ollama"
    ollama_chat.assert_called_once()
    _, kwargs = ollama_chat.call_args
    assert kwargs["model"] == llm.FALLBACK_CHAT_MODEL
    assert kwargs["messages"] == [{"role": "user", "content": "mi prompt"}]


def test_generar_respuesta_fallback_cuando_groq_devuelve_http_error(aura_modules, monkeypatch):
    llm = aura_modules.llm_service
    monkeypatch.setattr(llm, "GROQ_API_KEY", "test-key-123")

    class ErrorResponse:
        def raise_for_status(self):
            raise RuntimeError("HTTP 429 Too Many Requests")

    class Client429:
        def __init__(self, *args, **kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def post(self, *args, **kwargs):
            return ErrorResponse()

    monkeypatch.setattr(llm.httpx, "Client", Client429)
    ollama_chat = MagicMock(return_value={"message": {"content": "fallback"}})
    monkeypatch.setattr(llm.ollama_client, "chat", ollama_chat)

    assert llm.generar_respuesta("p") == "fallback"


def test_generar_respuesta_sin_key_va_directo_a_ollama(aura_modules, monkeypatch):
    llm = aura_modules.llm_service
    monkeypatch.setattr(llm, "GROQ_API_KEY", None)
    client_instanciado = []

    class UnexpectedClient:
        def __init__(self, *args, **kwargs):
            client_instanciado.append(True)

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def post(self, *args, **kwargs):
            raise AssertionError("no debería llamarse a Groq sin API key")

    monkeypatch.setattr(llm.httpx, "Client", UnexpectedClient)
    ollama_chat = MagicMock(return_value={"message": {"content": "respuesta de ollama"}})
    monkeypatch.setattr(llm.ollama_client, "chat", ollama_chat)

    resultado = llm.generar_respuesta("mi prompt")

    assert resultado == "respuesta de ollama"
    assert client_instanciado == []


def test_groq_model_default_es_gpt_oss_120b(aura_modules):
    """El default de producción apunta a un modelo VIVO de Groq, no al retirado
    llama-3.3-70b-versatile (que devolvía 404 model_not_found)."""
    llm = aura_modules.llm_service

    assert llm.GROQ_MODEL == "openai/gpt-oss-120b"


def test_groq_model_se_puede_overridear_por_env(aura_modules, monkeypatch):
    """GROQ_MODEL debe resolverse EN TIEMPO DE LLAMADA dentro de
    generar_respuesta(): el payload saliente debe llevar el id inyectado por
    env, no la constante de import."""
    llm = aura_modules.llm_service
    monkeypatch.setattr(llm, "GROQ_API_KEY", "test-key-123")
    monkeypatch.setenv("GROQ_MODEL", "openai/gpt-oss-20b")
    capturado = {}

    class FakeResponse:
        def raise_for_status(self):
            return None

        def json(self):
            return {"choices": [{"message": {"content": "respuesta de groq"}}]}

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def post(self, url, json=None, headers=None):
            capturado["payload"] = json
            return FakeResponse()

    monkeypatch.setattr(llm.httpx, "Client", FakeClient)

    resultado = llm.generar_respuesta("mi prompt")

    assert resultado == "respuesta de groq"
    assert capturado["payload"]["model"] == "openai/gpt-oss-20b"


def test_404_model_not_found_cae_a_ollama(aura_modules, monkeypatch):
    """Un 404 model_not_found de Groq (forma real de la API) debe caer al
    fallback de Ollama: una sola llamada con FALLBACK_CHAT_MODEL, y la petición
    saliente debe haber pedido el modelo vivo por defecto."""
    llm = aura_modules.llm_service
    monkeypatch.setattr(llm, "GROQ_API_KEY", "test-key-123")
    capturado = {}

    class ModelNotFoundResponse:
        def raise_for_status(self):
            request = llm.httpx.Request("POST", llm.GROQ_API_URL)
            response = llm.httpx.Response(
                404,
                request=request,
                json={
                    "error": {
                        "message": "The model `llama-3.3-70b-versatile` does not exist.",
                        "type": "invalid_request_error",
                        "code": "model_not_found",
                    }
                },
            )
            raise llm.httpx.HTTPStatusError(
                "model_not_found", request=request, response=response
            )

    class Client404:
        def __init__(self, *args, **kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def post(self, url, json=None, headers=None):
            capturado["payload"] = json
            return ModelNotFoundResponse()

    monkeypatch.setattr(llm.httpx, "Client", Client404)
    ollama_chat = MagicMock(return_value={"message": {"content": "respuesta de ollama"}})
    monkeypatch.setattr(llm.ollama_client, "chat", ollama_chat)

    resultado = llm.generar_respuesta("mi prompt")

    assert capturado["payload"]["model"] == "openai/gpt-oss-120b"
    assert resultado == "respuesta de ollama"
    ollama_chat.assert_called_once()
    _, kwargs = ollama_chat.call_args
    assert kwargs["model"] == llm.FALLBACK_CHAT_MODEL