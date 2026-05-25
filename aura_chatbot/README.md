# Aura Chatbot — Servicio IA

Servicio de chatbot inteligente con IA para el sistema AURA.
Desarrollado con FastAPI.

> Este servicio forma parte del monorepo **AURA**.  
> Para levantar todo el stack: `docker compose up -d` en la raíz del proyecto.

---

## Stack

| Componente | Tecnología |
|------------|-----------|
| Framework  | FastAPI + Uvicorn |
| Chat       | Groq API (`llama-3.3-70b-versatile`) |
| Embeddings | Ollama (`nomic-embed-text`) local |
| Vector DB  | ChromaDB (persistente en `./chroma_db/`) |
| Historial  | SQLite (`./data/historial.db`) |

---

## Estructura del proyecto

```
aura_chatbot/
├── main.py                  ← FastAPI app (endpoints REST)
├── chat_service.py          ← lógica del chat + historial
├── document_processor.py    ← chunking + embeddings de PDFs
├── llm_service.py           ← Groq API + Ollama embeddings
├── requirements.txt
├── pdfs/                    ← PDFs subidos por el usuario
├── data/
│   └── historial.db         ← SQLite (se crea automáticamente)
└── chroma_db/               ← ChromaDB (vectores, se crea automáticamente)
```

---

## Requisitos previos

### Opción 1 — Docker (recomendada)
- Docker y Docker Compose instalados
- `GROQ_API_KEY` configurada en el archivo `.env` raíz del monorepo
- Ejecutar `docker compose up -d` desde la raíz del monorepo

### Opción 2 — Desarrollo local
- Python 3.10 o superior
- Ollama instalado localmente con `nomic-embed-text`
- `GROQ_API_KEY` en `.env` dentro de `aura_chatbot/`

---

## Endpoints

| Método | Ruta                    | Descripción                          |
|--------|-------------------------|--------------------------------------|
| POST   | `/sesion/nueva`         | Crear nueva sesión de chat           |
| POST   | `/chat`                 | Enviar mensaje (requiere `sesion_id`)|
| POST   | `/documentos/subir`     | Subir un PDF para procesar           |
| GET    | `/documentos`           | Listar documentos subidos            |
| GET    | `/chat/historial/{id}`  | Obtener historial de una sesión      |

---

## Límites del free tier de Groq

| Límite             | Valor         |
|--------------------|---------------|
| Requests por minuto| 30            |
| Requests por día   | 14.400        |
| Tokens por minuto  | 6.000         |

---

## Solución de problemas

**`GROQ_API_KEY not found`** → Verificá que el archivo `.env` raíz del monorepo tenga `GROQ_API_KEY=gsk_...`.

**`connection refused` al generar embeddings** → Verificá que el contenedor de Ollama esté corriendo (`docker compose ps`).

**`model not found`** → Ejecutá `docker compose exec ollama ollama pull nomic-embed-text`.

**`429 Too Many Requests`** → Superaste el límite por minuto de Groq. Esperá 60 segundos y reintentá.

**El chat no responde desde el frontend** → Verificá que los 3 servicios estén corriendo (`docker compose ps`).
