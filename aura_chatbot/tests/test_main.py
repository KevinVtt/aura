"""Endpoint tests for main.py — every route of the FastAPI app, with the LLM /
embedding / ChromaDB / SQLite layers mocked at the boundary. Includes tests for
the SEC-05 (path traversal) and CORS fixes — uploads are sanitized and CORS no
longer uses a wildcard."""

import os
import uuid

import pytest


# ── GET / ────────────────────────────────────────────────────────────────────

def test_raiz(client):
    resp = client.get("/")
    assert resp.status_code == 200
    assert resp.json()["estado"] == "ok"


@pytest.mark.asyncio
async def test_raiz_async(aura_modules):
    """Async path through httpx ASGITransport (justifies pytest-asyncio)."""
    from httpx import ASGITransport, AsyncClient

    transport = ASGITransport(app=aura_modules.main.app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.get("/")
    assert resp.status_code == 200
    assert resp.json()["estado"] == "ok"


# ── POST /sesion/nueva ───────────────────────────────────────────────────────

def test_nueva_sesion_devuelve_uuid(client):
    resp = client.post("/sesion/nueva")
    assert resp.status_code == 200
    sesion_id = resp.json()["sesion_id"]
    uuid.UUID(sesion_id)  # lanza si no es un UUID válido


# ── POST /documentos/subir ───────────────────────────────────────────────────

def test_subir_documento_pdf_valido(client, aura_modules, monkeypatch):
    procesado = {}

    def fake_procesar(ruta, nombre):
        procesado["ruta"] = ruta
        procesado["nombre"] = nombre

    monkeypatch.setattr(aura_modules.main.dp, "procesar_pdf", fake_procesar)

    resp = client.post(
        "/documentos/subir",
        files={"archivo": ("manual.pdf", b"%PDF-1.4 fake", "application/pdf")},
    )

    assert resp.status_code == 200
    assert resp.json() == {"mensaje": "'manual.pdf' procesado correctamente."}
    assert procesado["nombre"] == "manual.pdf"
    assert procesado["ruta"] == "./pdfs/manual.pdf"
    assert os.path.exists("./pdfs/manual.pdf")
    os.remove("./pdfs/manual.pdf")


def test_subir_documento_rechaza_no_pdf(client):
    resp = client.post(
        "/documentos/subir",
        files={"archivo": ("malware.exe", b"MZ", "application/octet-stream")},
    )
    assert resp.status_code == 400
    assert resp.json()["detail"] == "Solo se aceptan archivos PDF."


def test_subir_documento_pdf_sin_texto_devuelve_400_con_mensaje_controlado(client, aura_modules, monkeypatch):
    """SEC-11 (corregido): solo se expone el mensaje controlado de ValueError."""

    def raiser(ruta, nombre):
        raise ValueError("No se pudo extraer texto del PDF.")

    monkeypatch.setattr(aura_modules.main.dp, "procesar_pdf", raiser)

    resp = client.post(
        "/documentos/subir",
        files={"archivo": ("vacio.pdf", b"%PDF-1.4", "application/pdf")},
    )

    assert resp.status_code == 400
    assert resp.json()["detail"] == "No se pudo extraer texto del PDF."


def test_subir_documento_error_inesperado_no_filtra_detalle(client, aura_modules, monkeypatch):
    """SEC-11 (corregido): un error inesperado no expone el mensaje interno."""

    def raiser(ruta, nombre):
        raise RuntimeError("connection to 10.0.0.5:11434 refused")

    monkeypatch.setattr(aura_modules.main.dp, "procesar_pdf", raiser)

    resp = client.post(
        "/documentos/subir",
        files={"archivo": ("manual.pdf", b"%PDF-1.4", "application/pdf")},
    )

    assert resp.status_code == 500
    assert resp.json()["detail"] == "No se pudo procesar el PDF."
    assert "10.0.0.5" not in resp.text


def test_subir_documento_sanitiza_path_traversal(client, aura_modules, monkeypatch):
    """SEC-05 (corregido): un filename con ``../`` se reduce a su basename y el
    archivo se escribe DENTRO de ./pdfs/, nunca fuera."""
    procesado = {}
    monkeypatch.setattr(
        aura_modules.main.dp,
        "procesar_pdf",
        lambda ruta, nombre: procesado.update(ruta=ruta, nombre=nombre),
    )

    resp = client.post(
        "/documentos/subir",
        files={"archivo": ("../evil.pdf", b"%PDF-1.4 fake", "application/pdf")},
    )

    assert resp.status_code == 200
    assert procesado["nombre"] == "evil.pdf"
    assert procesado["ruta"] == "./pdfs/evil.pdf"
    assert os.path.exists("./pdfs/evil.pdf")
    assert not os.path.exists("./evil.pdf")
    os.remove("./pdfs/evil.pdf")


def test_subir_documento_rechaza_pdf_fuera_de_carpeta_con_extension_no_pdf(client, aura_modules, monkeypatch):
    """Solo se aceptan .pdf (case-insensitive); ``../evil.txt`` se rechaza."""
    monkeypatch.setattr(aura_modules.main.dp, "procesar_pdf", lambda ruta, nombre: None)

    resp = client.post(
        "/documentos/subir",
        files={"archivo": ("../evil.txt", b"data", "text/plain")},
    )

    assert resp.status_code == 400
    assert not os.path.exists("./evil.txt")
    assert not os.path.exists("./pdfs/evil.txt")


def test_cors_no_usa_wildcard(aura_modules):
    """SEC: el CORS debe listar orígenes explícitos, no ``*``."""
    main = aura_modules.main
    cors = [m for m in main.app.user_middleware if m.cls.__name__ == "CORSMiddleware"]
    assert cors, "CORSMiddleware no configurado"
    origins = cors[0].kwargs["allow_origins"]
    assert "*" not in origins
    assert "http://localhost:3000" in origins


# ── GET /documentos ──────────────────────────────────────────────────────────

def test_listar_documentos(client, aura_modules, monkeypatch):
    monkeypatch.setattr(aura_modules.main.dp, "listar_documentos", lambda: ["a.pdf", "b.pdf"])
    resp = client.get("/documentos")
    assert resp.status_code == 200
    assert resp.json() == {"documentos": ["a.pdf", "b.pdf"]}


# ── POST /chat ───────────────────────────────────────────────────────────────

def test_chat_responde(client, aura_modules, monkeypatch):
    resultado = {
        "respuesta": "hola",
        "fuentes": ["a.pdf"],
        "historial": [{"rol": "user", "contenido": "hola"}],
    }
    monkeypatch.setattr(aura_modules.main.cs, "responder", lambda sid, pregunta: resultado)

    resp = client.post("/chat", json={"sesion_id": "s1", "pregunta": "hola"})

    assert resp.status_code == 200
    assert resp.json() == resultado


def test_chat_pregunta_vacia_espacios(client):
    resp = client.post("/chat", json={"sesion_id": "s1", "pregunta": "   "})
    assert resp.status_code == 400
    assert resp.json()["detail"] == "La pregunta no puede estar vacía."


def test_chat_pregunta_vacia_string(client):
    resp = client.post("/chat", json={"sesion_id": "s1", "pregunta": ""})
    assert resp.status_code == 400


def test_chat_faltan_campos_devuelve_422(client):
    resp = client.post("/chat", json={"pregunta": "hola"})
    assert resp.status_code == 422


# ── GET /chat/historial/{sesion_id} ──────────────────────────────────────────

def test_ver_historial(client, aura_modules, monkeypatch):
    hist = [{"rol": "user", "contenido": "hola"}]
    monkeypatch.setattr(aura_modules.main.cs, "obtener_historial", lambda sid: hist)

    resp = client.get("/chat/historial/s1")

    assert resp.status_code == 200
    assert resp.json() == {"historial": hist}


# ── DELETE /chat/historial/{sesion_id} ───────────────────────────────────────

def test_limpiar_historial(client, aura_modules, monkeypatch):
    limpiado = []
    monkeypatch.setattr(aura_modules.main.cs, "limpiar_historial", lambda sid: limpiado.append(sid))

    resp = client.delete("/chat/historial/s1")

    assert resp.status_code == 200
    assert resp.json() == {"mensaje": "Historial limpiado."}
    assert limpiado == ["s1"]


# ── DELETE /documentos/{nombre_doc} ──────────────────────────────────────────

def test_eliminar_documento_ok(client, aura_modules, monkeypatch):
    monkeypatch.setattr(aura_modules.main.dp, "eliminar_documento", lambda nombre: None)

    resp = client.delete("/documentos/a.pdf")

    assert resp.status_code == 200
    assert resp.json() == {"mensaje": "'a.pdf' eliminado correctamente."}


def test_eliminar_documento_no_encontrado_devuelve_404(client, aura_modules, monkeypatch):
    def raiser(nombre):
        raise ValueError(f"No se encontró '{nombre}' en la base de datos.")

    monkeypatch.setattr(aura_modules.main.dp, "eliminar_documento", raiser)

    resp = client.delete("/documentos/inexistente.pdf")

    assert resp.status_code == 404
    assert "No se encontró" in resp.json()["detail"]


def test_eliminar_documento_error_interno_devuelve_500(client, aura_modules, monkeypatch):
    def raiser(nombre):
        raise RuntimeError("boom")

    monkeypatch.setattr(aura_modules.main.dp, "eliminar_documento", raiser)

    resp = client.delete("/documentos/a.pdf")

    assert resp.status_code == 500
    assert resp.json()["detail"] == "No se pudo eliminar el documento."
    assert "boom" not in resp.text

# -- POST /chat - errores LLM ----------------------------------------------

def test_chat_devuelve_502_cuando_el_llm_falla(client, aura_modules, monkeypatch):
    def raiser(sid, pregunta):
        raise RuntimeError('llama-server process has terminated: signal: killed')

    monkeypatch.setattr(aura_modules.main.cs, 'responder', raiser)

    resp = client.post('/chat', json={'sesion_id': 's', 'pregunta': 'hola'})

    assert resp.status_code == 502
    assert resp.json()['detail'] == "El servicio de IA no está disponible."
    assert "llama-server" not in resp.text

