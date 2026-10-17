import os
import sqlite3
from document_processor import buscar_chunks_relevantes
from llm_service import generar_respuesta

DB_PATH = "./data/historial.db"

# Cuántos turnos ve el LLM en cada llamada.
# El historial completo queda guardado en SQLite sin límite.
TURNOS_EN_PROMPT = 10


def _init_db():
    """
    Crea la tabla de historial si no existe.
    Se ejecuta una sola vez al iniciar el servidor.
    """
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    con = sqlite3.connect(DB_PATH)
    con.execute("""
        CREATE TABLE IF NOT EXISTS historial (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            sesion_id TEXT    NOT NULL,
            rol       TEXT    NOT NULL,
            contenido TEXT    NOT NULL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """)
    con.commit()
    con.close()


def _guardar_mensaje(sesion_id: str, rol: str, contenido: str):
    """Inserta un mensaje en la base de datos."""
    con = sqlite3.connect(DB_PATH)
    con.execute(
        "INSERT INTO historial (sesion_id, rol, contenido) VALUES (?, ?, ?)",
        (sesion_id, rol, contenido)
    )
    con.commit()
    con.close()


def _obtener_historial_db(sesion_id: str, limite: int | None = None) -> list[dict]:
    """
    Devuelve los mensajes de una sesión ordenados por fecha (ascendente).
    Sin límite devuelve el historial completo; con `limite` solo los últimos
    mensajes, para no cargar todo el historial en cada llamada al LLM.
    """
    con = sqlite3.connect(DB_PATH)
    if limite is None:
        rows = con.execute(
            "SELECT rol, contenido FROM historial WHERE sesion_id = ? ORDER BY id ASC",
            (sesion_id,)
        ).fetchall()
    else:
        rows = con.execute(
            "SELECT rol, contenido FROM historial WHERE sesion_id = ? ORDER BY id DESC LIMIT ?",
            (sesion_id, limite)
        ).fetchall()
        rows = rows[::-1]
    con.close()
    return [{"rol": rol, "contenido": contenido} for rol, contenido in rows]


def _construir_prompt(pregunta: str, chunks: list[dict], historial: list[dict]) -> str:
    contexto = ""
    for i, chunk in enumerate(chunks, 1):
        contexto += f"\n[Fragmento {i}]\n{chunk['texto']}\n"

    mensajes_en_prompt = TURNOS_EN_PROMPT * 2
    historial_reciente = historial[-mensajes_en_prompt:] if len(historial) > mensajes_en_prompt else historial

    # Filtrá respuestas negativas del historial
    historial_texto = ""
    for msg in historial_reciente:
        if msg["rol"] == "assistant" and "No tengo información" in msg["contenido"]:
            continue
        rol = "Usuario" if msg["rol"] == "user" else "Asistente"
        historial_texto += f"{rol}: {msg['contenido']}\n"

    prompt = f"""Sos AURA, el asistente virtual de la empresa. Tenés un trato cordial, cercano y profesional.

Cómo responder:
- Si el usuario te saluda, se despide, agradece o hace charla casual (por ejemplo "Hola", "Buenos días", "¿Cómo estás?", "Gracias"), respondé de forma amable y natural, y ofrecé ayudarlo con sus consultas. Nunca digas que no podés procesar el mensaje.
- Si el usuario hace una pregunta sobre la información de la empresa, respondela usando los FRAGMENTOS provistos, de forma concisa y directa.
- Si la pregunta es sobre la empresa pero los FRAGMENTOS no contienen la respuesta, respondé con amabilidad empezando con "No tengo información" sobre ese tema, y ofrecé ayudarlo con otra consulta.
- No inventes datos que no estén en los FRAGMENTOS.

FRAGMENTOS:
{contexto}

{"CONVERSACIÓN PREVIA:" + chr(10) + historial_texto if historial_texto else ""}

PREGUNTA: {pregunta}
RESPUESTA:"""

    return prompt

def responder(sesion_id: str, pregunta: str) -> dict:
    # 1. Recuperar solo los últimos turnos que entran en el prompt
    historial = _obtener_historial_db(sesion_id, TURNOS_EN_PROMPT * 2)

    # 2. Buscar contexto relevante en ChromaDB
    chunks = buscar_chunks_relevantes(pregunta, top_k=3)

    # 3. Construir prompt con los últimos N turnos
    prompt = _construir_prompt(pregunta, chunks, historial)

    # 4. Llamar al LLM
    respuesta = generar_respuesta(prompt)

    # 5. Guardar pregunta y respuesta en SQLite
    _guardar_mensaje(sesion_id, "user", pregunta)
    _guardar_mensaje(sesion_id, "assistant", respuesta)

    # 6. Devolver
    fuentes = sorted(set(c["fuente"] for c in chunks))
    historial_actualizado = _obtener_historial_db(sesion_id)
    return {
        "respuesta": respuesta,
        "fuentes":   fuentes,
        "historial": historial_actualizado
    }


def obtener_historial(sesion_id: str) -> list[dict]:
    return _obtener_historial_db(sesion_id)


def limpiar_historial(sesion_id: str):
    con = sqlite3.connect(DB_PATH)
    con.execute("DELETE FROM historial WHERE sesion_id = ?", (sesion_id,))
    con.commit()
    con.close()


# Inicializar la base de datos al importar el módulo
_init_db()