# Aura Chatbot — Rama `feature/ia/groq`

## ¿Qué cambia en esta rama?
Se reemplaza el modelo de chat local (Ollama/gemma3) por **Groq API**,
que responde en ~1-2 segundos usando hardware especializado (LPU).
Los embeddings siguen corriendo con Ollama local (nomic-embed-text),
por lo que no es necesario borrar chroma_db/ ni volver a subir los PDFs.

---

## Estructura del proyecto
```
aura_chatbot/
├── main.py                  ← ChatbotController (API REST)
├── chat_service.py          ← ChatService (lógica del chat + historial)
├── document_processor.py    ← DocumentProcessor (chunking + embeddings)
├── llm_service.py           ← LLMService (Groq API + Ollama embeddings)
├── requirements.txt
├── .env                     ← GROQ_API_KEY=gsk_...
├── pdfs/                    ← Los PDFs que subas se guardan acá
├── historial.db             ← SQLite (se crea solo)
└── chroma_db/               ← ChromaDB guarda los vectores acá (se crea solo)
```

---

## PASO 1 — Obtener la API key de Groq (gratuita)

1. Entrá a https://console.groq.com
2. Creá una cuenta gratuita
3. **API Keys** → **Create API Key**
4. Copiá la key, empieza con `gsk_...`

---

## PASO 2 — Configurar el archivo .env

Creá un archivo `.env` en la raíz del proyecto con este contenido:
```
GROQ_API_KEY=gsk_TU_KEY_ACA
```

---

## PASO 3 — Instalar Ollama (solo para embeddings)

1. Entrá a https://ollama.com/download
2. Descargá el instalador para Windows y ejecutalo
3. Al terminar, Ollama queda corriendo como servicio en segundo plano

Descargá el modelo de embeddings (una sola vez):
```bash
ollama pull nomic-embed-text
```

Ya no es necesario descargar gemma3 — el chat lo maneja Groq.

---

## PASO 4 — Instalar dependencias Python
```bash
pip install -r requirements.txt
```

El `requirements.txt` de esta rama incluye:
```
fastapi>=0.115.0
uvicorn>=0.30.6
python-multipart>=0.0.9
pypdf>=4.3.1
chromadb>=1.0.0
ollama>=0.3.0
groq>=0.9.0
python-dotenv>=1.0.1
```

---

## PASO 5 — Levantar el servidor
```bash
python -m uvicorn main:app --reload
```

---

## PASO 6 — Usar el chatbot

Abrí: http://127.0.0.1:8000/docs

1. `POST /documentos/subir` → subís tu PDF
2. `POST /sesion/nueva` → copiás el sesion_id
3. `POST /chat` → hacés preguntas con ese sesion_id
4. Seguí mandando preguntas con el mismo sesion_id para mantener el historial

---

## Comparativa de velocidad

| Componente     | Rama main (Ollama) | Esta rama (Groq)       |
|----------------|--------------------|------------------------|
| Chat           | gemma3 local ~30s  | llama-3.3-70b ~1-2s    |
| Embeddings     | nomic-embed-text   | nomic-embed-text       |
| Requiere inet  | No                 | Solo para el chat      |
| Costo          | Gratis             | Gratis (1500 req/día)  |

---

## Límites del free tier de Groq

| Límite             | Valor         |
|--------------------|---------------|
| Requests por minuto| 30            |
| Requests por día   | 14.400        |
| Tokens por minuto  | 6.000         |

Para un chatbot de pruebas es más que suficiente.

---

## Requisitos de hardware

| RAM        | Disco libre |
|------------|-------------|
| 4 GB mínimo| 1 GB mínimo |

Al mover el chat a Groq, los requisitos de hardware bajan considerablemente
ya que el modelo grande ya no corre en tu PC.

---

## Solución de problemas

`GROQ_API_KEY not found` → Verificá que el archivo `.env` existe en la carpeta del proyecto y tiene el formato correcto.

`connection refused` al generar embeddings → Ollama no está corriendo, abrilo desde el menú inicio.

`model not found` → Ejecutá `ollama pull nomic-embed-text`.

`429 Too Many Requests` → Superaste el límite por minuto de Groq. Esperá 60 segundos y reintentá.

`CLOSE_WAIT en el puerto 8000` → Ejecutá `taskkill /PID <pid> /F` y reiniciá el servidor.