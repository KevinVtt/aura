"""Unit tests for chat_service.py — prompt construction, the responder flow
(with LLM/ChromaDB mocked) and real SQLite round-trips against the temp
hermetic environment (./data/historial.db lives in the pytest temp dir)."""

import uuid

from unittest.mock import MagicMock


# ── _construir_prompt ────────────────────────────────────────────────────────

def test_construir_prompt_incluye_fragmentos_historial_y_pregunta(aura_modules):
    cs = aura_modules.chat_service
    chunks = [{"texto": "fragmento uno"}]
    historial = [
        {"rol": "user", "contenido": "hola"},
        {"rol": "assistant", "contenido": "chau"},
    ]
    prompt = cs._construir_prompt("¿qué es AURA?", chunks, historial)
    assert "[Fragmento 1]" in prompt
    assert "fragmento uno" in prompt
    assert "CONVERSACIÓN PREVIA:" in prompt
    assert "Usuario: hola" in prompt
    assert "Asistente: chau" in prompt
    assert "PREGUNTA: ¿qué es AURA?" in prompt


def test_construir_prompt_filtra_respuestas_sin_informacion(aura_modules):
    cs = aura_modules.chat_service
    historial = [{"rol": "assistant", "contenido": "No tengo información sobre ese tema."}]
    prompt = cs._construir_prompt("p", [], historial)
    # El mensaje del historial se excluye del prompt (no aparece como "Asistente: ...")
    assert "Asistente: No tengo información" not in prompt
    assert "CONVERSACIÓN PREVIA:" not in prompt


def test_construir_prompt_sin_historial(aura_modules):
    cs = aura_modules.chat_service
    prompt = cs._construir_prompt("p", [], [])
    assert "CONVERSACIÓN PREVIA:" not in prompt
    assert "PREGUNTA: p" in prompt


def test_construir_prompt_trunca_historial_a_ultimos_turnos(aura_modules):
    cs = aura_modules.chat_service
    historial = [{"rol": "user", "contenido": f"msg{i}"} for i in range(25)]
    prompt = cs._construir_prompt("p", [], historial)
    assert prompt.count("Usuario:") == 20
    assert "msg0" not in prompt
    assert "msg24" in prompt


# ── responder ────────────────────────────────────────────────────────────────

def test_responder_flujo_completo(aura_modules, monkeypatch):
    cs = aura_modules.chat_service
    historial_inicial = [{"rol": "user", "contenido": "previo"}]
    historial_final = historial_inicial + [
        {"rol": "user", "contenido": "pregunta"},
        {"rol": "assistant", "contenido": "respuesta"},
    ]
    obtener = MagicMock(side_effect=[historial_inicial, historial_final])
    monkeypatch.setattr(cs, "_obtener_historial_db", obtener)
    chunks = [
        {"texto": "t1", "fuente": "a.pdf"},
        {"texto": "t2", "fuente": "a.pdf"},
        {"texto": "t3", "fuente": "b.pdf"},
    ]
    monkeypatch.setattr(cs, "buscar_chunks_relevantes", MagicMock(return_value=chunks))
    monkeypatch.setattr(cs, "generar_respuesta", MagicMock(return_value="respuesta"))
    guardados = []
    monkeypatch.setattr(
        cs, "_guardar_mensaje", lambda sid, rol, contenido: guardados.append((sid, rol, contenido))
    )

    resultado = cs.responder("s1", "pregunta")

    assert resultado["respuesta"] == "respuesta"
    # fuentes deduplicadas (a.pdf aparece 2 veces en los chunks)
    assert sorted(resultado["fuentes"]) == ["a.pdf", "b.pdf"]
    assert resultado["historial"] == historial_final
    assert guardados == [("s1", "user", "pregunta"), ("s1", "assistant", "respuesta")]
    # El prompt carga solo los últimos turnos; el historial devuelto es completo.
    assert obtener.call_args_list[0].args == ("s1", cs.TURNOS_EN_PROMPT * 2)
    assert obtener.call_args_list[1].args == ("s1",)


def test_responder_no_cita_fuentes_cuando_no_encontro_la_respuesta(aura_modules, monkeypatch):
    """Citar "Fuente: x.pdf" debajo de un "No tengo información" se contradice:
    si el modelo no encontró la respuesta, los fragmentos no eran la fuente."""
    cs = aura_modules.chat_service
    monkeypatch.setattr(cs, "_obtener_historial_db", MagicMock(return_value=[]))
    monkeypatch.setattr(cs, "buscar_chunks_relevantes", MagicMock(return_value=[
        {"texto": "t1", "fuente": "guia.pdf"},
    ]))
    monkeypatch.setattr(cs, "generar_respuesta", MagicMock(
        return_value="No tengo información sobre ese tema. ¿Hay algo más en lo que pueda ayudarte?"
    ))
    monkeypatch.setattr(cs, "_guardar_mensaje", lambda *a: None)

    resultado = cs.responder("s1", "¿cuántos días de vacaciones me corresponden?")

    assert resultado["fuentes"] == []


def test_responder_cita_fuentes_cuando_si_respondio(aura_modules, monkeypatch):
    cs = aura_modules.chat_service
    monkeypatch.setattr(cs, "_obtener_historial_db", MagicMock(return_value=[]))
    monkeypatch.setattr(cs, "buscar_chunks_relevantes", MagicMock(return_value=[
        {"texto": "t1", "fuente": "guia.pdf"},
    ]))
    monkeypatch.setattr(cs, "generar_respuesta", MagicMock(return_value="El profesor es Fulano."))
    monkeypatch.setattr(cs, "_guardar_mensaje", lambda *a: None)

    resultado = cs.responder("s1", "¿quién es el profesor?")

    assert resultado["fuentes"] == ["guia.pdf"]


# ── SQLite real (hermético: ./data/historial.db vive en el temp dir) ─────────

def test_guardar_y_obtener_mensaje_sqlite_real(aura_modules):
    cs = aura_modules.chat_service
    sid = str(uuid.uuid4())
    cs._guardar_mensaje(sid, "user", "hola")
    cs._guardar_mensaje(sid, "assistant", "chau")

    historial = cs._obtener_historial_db(sid)

    assert historial == [
        {"rol": "user", "contenido": "hola"},
        {"rol": "assistant", "contenido": "chau"},
    ]
    cs.limpiar_historial(sid)
    assert cs._obtener_historial_db(sid) == []


def test_limpiar_historial_sesion_inexistente_no_falla(aura_modules):
    cs = aura_modules.chat_service
    cs.limpiar_historial(str(uuid.uuid4()))  # no debe lanzar


def test_init_db_idempotente(aura_modules):
    cs = aura_modules.chat_service
    cs._init_db()
    cs._init_db()  # no debe lanzar


def test_init_db_crea_directorio_data_si_no_existe(aura_modules, monkeypatch, tmp_path):
    """FAST-01: _init_db() debe crear el directorio de ./data antes de
    conectar, para que el servicio no crashee fuera de Docker."""
    cs = aura_modules.chat_service
    destino = tmp_path / "data_inexistente" / "historial.db"
    assert not destino.parent.exists()
    monkeypatch.setattr(cs, "DB_PATH", str(destino))

    cs._init_db()

    assert destino.parent.is_dir()
    assert destino.exists()


def test_obtener_historial_db_limite_trae_recientes_ascendente(aura_modules):
    """El límite debe devolver los últimos mensajes, en orden ascendente."""
    cs = aura_modules.chat_service
    sid = str(uuid.uuid4())
    for i in range(6):
        cs._guardar_mensaje(sid, "user", f"m{i}")

    reciente = cs._obtener_historial_db(sid, limite=3)

    assert reciente == [
        {"rol": "user", "contenido": "m3"},
        {"rol": "user", "contenido": "m4"},
        {"rol": "user", "contenido": "m5"},
    ]
    # Sin límite (y vía obtener_historial) sigue devolviendo TODO
    completo = [{"rol": "user", "contenido": f"m{i}"} for i in range(6)]
    assert cs._obtener_historial_db(sid) == completo
    assert cs.obtener_historial(sid) == completo
    cs.limpiar_historial(sid)