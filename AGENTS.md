# AURA — Project Context

Monorepo with three services: frontend (React), backend (Spring Boot), ai (FastAPI).

## Directory Layout

```
deploy/
├── frontend/aura-frontend/  # React 18 (CRA, NOT Next.js)
├── backend/project-aura/    # Spring Boot 3.5 + Java 21 + Maven
├── aura_chatbot/            # FastAPI (Python)
├── docker-compose.yml
├── .env                     # Root env (GROQ_API_KEY, DB_PASS, JWT_SECRET, ADMIN_PASSWORD) — gitignored
├── .env.example             # Plantilla con las variables requeridas
└── AGENTS.md
```

## Services

### `frontend/aura-frontend/`
- **React 18 with react-scripts (CRA)** — `npm start` → port 3000, `npm run build` → nginx on port 80
- No lint/typecheck scripts configured
- API calls via `src/api/axios.js` — auto-attaches JWT from localStorage, redirects to `/login` on 401
- `REACT_APP_API_URL` env var (defaults to `http://localhost:8080`)
- Routes: `/login` (public), `/admin` (ADMIN only), `/chat` (ADMIN+EMPLEADO)

### `backend/project-aura/`
- **Spring Boot 3.5 + Java 21 + Maven** — `./mvnw spring-boot:run` → port 8080
- Multi-stage Docker: `maven:3.9-eclipse-temurin-21` build → `eclipse-temurin:21-jre` runtime
- DB: PostgreSQL via JPA/Hibernate, `ddl-auto=update` (no Flyway/Liquibase)
- JWT auth (24h expiry), `jwt.secret` comes from the `JWT_SECRET` env var (no default; startup fails without it)
- Layer structure: Controller → Service → Repository
- `fastapi.url` injected via `IA_SERVICE_URL` env (default `http://localhost:8000`)
- First admin: seeded only when the DB is empty, from `ADMIN_EMAIL` / `ADMIN_PASSWORD` (no default password)
- Lombok enabled, JJWT 0.11.5 for tokens
- Tests: `./mvnw test` (unit + `@WebMvcTest`; `ProjectAuraApplicationTests` needs a live DB)

### `aura_chatbot/`
- **FastAPI + Uvicorn** — `uvicorn main:app --reload` → port 8000
- Chat: **Groq API** (`llama-3.3-70b-versatile`) + embeddings: **Ollama** (`nomic-embed-text`) local
- `GROQ_API_KEY` required in `.env` or root `.env`
- `OLLAMA_HOST` env (default `http://localhost:11434`)
- `ALLOWED_ORIGINS` env (comma-separated CORS origins, default `http://localhost:3000,http://localhost`)
- Vector store: ChromaDB (persistent at `./chroma_db/`)
- Chat history: SQLite at `./data/historial.db`
- Endpoints: `POST /sesion/nueva`, `POST /chat`, `POST /documentos/subir`, `GET /documentos`, `GET /chat/historial/{id}`

## Docker Stack

```bash
docker compose up -d        # Start all services
docker compose up ia -d     # Start one service (service name is "ia", not "ai")
docker compose logs -f ia   # Watch logs
docker compose down         # Stop all
```

Service naming in compose: `frontend`, `backend`, `ia`, `ollama`. Volumes: `ollama_data`, `chroma_data`, `ia_data`, `pdfs_data`.

## Data Flow (AI Feature)

`frontend` → REST → `backend` (Spring Boot) → REST → `ia` (FastAPI) → Groq/Ollama

## LLM Integration

- Provider: **Groq** (chat) + **Ollama** (embeddings) — already decided, not abstract
- 30 req/min, 6000 tokens/min on Groq free tier
- `llm_service.py` is the single entry point if provider changes

## Key Conventions

- Code in English
- Commits: conventional (`feat:`, `fix:`, `chore:`)
- `.env` (root) and `application.properties` are gitignored and must never be committed; use `.env.example` and `application.properties.example` as templates
- Before changing API contracts, check impact on all 3 services
