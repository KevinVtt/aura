# AURA

Asistente virtual con IA para empresas. Permite consultar la documentación interna (PDF, Word y Excel) desde un chat, y administrar usuarios y documentos según el rol.

## Cómo funciona

```
Navegador ──REST──> backend (Spring Boot) ──REST──> IA (FastAPI) ──> Groq (respuestas)
                                                                 └──> Ollama (búsqueda)
```

- El **admin** da de alta usuarios y sube documentos.
- Los **empleados** solo usan el chat.
- La IA busca en los documentos subidos (ChromaDB) y responde citando solo esa información. Si no está, lo dice.

| Servicio | Tecnología | Puerto |
|----------|-----------|--------|
| frontend | React 18 | 3000 |
| backend | Spring Boot 3.5 + Java 21 | 8080 |
| ia | FastAPI (Python) | 8000 |
| Ollama | Embeddings locales | 11434 |
| PostgreSQL | Base de datos | 5432 |

## Requisitos

- **Java 21** y Maven (o usar el `mvnw` incluido)
- **Node.js 18+**
- **Python 3.11+** con `pip install -r aura_chatbot/requirements.txt`
- **PostgreSQL** con una base llamada `aura_db`
- **Ollama** con el modelo de embeddings: `ollama pull nomic-embed-text`
- Una **API key de Groq**: https://console.groq.com/keys

## Puesta en marcha

### 1. Crear la base de datos

```sql
CREATE DATABASE aura_db;
```

Las tablas se crean solas al arrancar el backend.

### 2. Configurar las claves

```bash
cp .env.example .env
```

Completar en `.env`:

| Variable | Para qué |
|----------|----------|
| `GROQ_API_KEY` | Respuestas del chat |
| `DB_PASS` | Contraseña de PostgreSQL |
| `JWT_SECRET` | Firma de la sesión. Usar un valor largo y aleatorio |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Primer administrador (solo si la base está vacía) |
| `SMTP_USER` / `SMTP_PASS` / `MAIL_FROM` | Envío del correo de alta de usuarios |
| `ALLOWED_EMAIL_DOMAINS` | Dominios permitidos, separados por coma. Vacío = cualquiera |
| `APP_URL` | Dirección del frontend para el enlace del correo (default `http://localhost:3000`) |

El `.env` no se versiona. Nunca subas claves al repositorio.

### 3. Levantar

Primero abrí **Ollama** en su propia ventana:

```bash
ollama serve
```

Después, doble clic en **`levantar.bat`** (raíz del proyecto). Abre tres ventanas: IA, backend y frontend. Esperá ~30 segundos y entrá a:

http://localhost:3000

### Levantar a mano (alternativa)

```bash
# IA
cd aura_chatbot
py -3 -m uvicorn main:app --host 127.0.0.1 --port 8000

# Backend
cd backend/project-aura
mvnw.cmd spring-boot:run

# Frontend
cd frontend/aura-frontend
npm install
npm start
```

Cada ventana necesita las variables del `.env` cargadas como variables de entorno.

### Con Docker (alternativa)

```bash
docker compose up -d
```

## Uso

1. Entrá con el usuario admin definido en `.env`.
2. En **Administración**, creá usuarios. Si dejás la contraseña vacía, se genera una y se envía por correo.
3. Subí los PDF, Word o Excel que la IA debe conocer.
4. El usuario nuevo entra con esa clave, el sistema le pide cambiarla, y ya puede chatear.

No hay registro público: todas las cuentas las crea el admin.

## Estructura

```
deploy/
├── frontend/aura-frontend/   # React 18
├── backend/project-aura/     # Spring Boot 3.5
├── aura_chatbot/             # FastAPI + Groq + Ollama + ChromaDB
├── levantar.bat              # Arranca los tres servicios
├── docker-compose.yml
├── .env.example
└── AGENTS.md                 # Contexto técnico para el equipo
```

## Tests

```bash
cd aura_chatbot && py -3 -m pytest tests -q
cd backend/project-aura && mvnw.cmd test
cd frontend/aura-frontend && npx react-scripts test --watchAll=false
```

## Problemas frecuentes

- **"No tengo información" en todo:** Ollama no está corriendo o falta el modelo (`ollama pull nomic-embed-text`).
- **El backend no arranca:** falta `JWT_SECRET` en `.env`, o la base `aura_db` no existe.
- **No llega el correo de alta:** revisar `SMTP_USER`, `SMTP_PASS` y que el enlace use la IP o dominio correcto en `APP_URL`.
- **La IA responde de otros documentos:** los fragmentos vienen de la búsqueda; conviene borrar documentos viejos que ya no se usan.

## Licencia

Proyecto académico.
