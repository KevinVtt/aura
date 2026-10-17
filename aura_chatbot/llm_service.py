import os
from dotenv import load_dotenv
import httpx
import ollama

load_dotenv()

GROQ_API_KEY = os.environ.get("GROQ_API_KEY")
GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
GROQ_MODEL = "openai/gpt-oss-120b"

ollama_host = os.environ.get("OLLAMA_HOST", "http://localhost:11434")
ollama_client = ollama.Client(host=ollama_host, timeout=30)

FALLBACK_CHAT_MODEL = "llama3.2:3b"
EMBED_MODEL = "nomic-embed-text"

# Ollama descarga el modelo de la RAM a los 5 minutos de inactividad y cargarlo
# otra vez cuesta ~2 s: medido, la primera pregunta despues de una pausa pagaba
# 2040 ms de embedding en vez de 37 ms.
KEEP_ALIVE = "30m"


def generar_embedding(texto: str) -> list[float]:
    """Embeddings con Ollama local."""
    response = ollama_client.embeddings(
        model=EMBED_MODEL, prompt=texto, keep_alive=KEEP_ALIVE
    )
    return response["embedding"]


def generar_respuesta(prompt: str) -> str:
    """Respuestas con Groq (rápido). Fallback a Ollama si Groq no está disponible."""
    if GROQ_API_KEY:
        try:
            headers = {
                "Authorization": f"Bearer {GROQ_API_KEY}",
                "Content-Type": "application/json",
            }
            payload = {
                "model": os.environ.get("GROQ_MODEL") or GROQ_MODEL,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.2,
                "max_tokens": 300,
                # gpt-oss es un modelo de razonamiento: en su nivel por defecto
                # gastaba ~107 de los 137 tokens de salida en "pensar" antes de
                # contestar. Con "low" la mediana bajo de 691 a 551 ms y el
                # consumo a un tercio. Ojo: no todos los modelos lo aceptan.
                "reasoning_effort": "low",
            }
            with httpx.Client(timeout=30) as client:
                resp = client.post(GROQ_API_URL, json=payload, headers=headers)
                resp.raise_for_status()
                return resp.json()["choices"][0]["message"]["content"]
        except Exception as e:
            print(f"[Groq error] {e}. Fallback a Ollama.")

    response = ollama_client.chat(
        model=FALLBACK_CHAT_MODEL,
        options={"temperature": 0.2, "num_predict": 300},
        messages=[{"role": "user", "content": prompt}],
    )
    return response["message"]["content"]
