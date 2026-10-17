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


def generar_embedding(texto: str) -> list[float]:
    """Embeddings con Ollama local."""
    response = ollama_client.embeddings(model=EMBED_MODEL, prompt=texto)
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
