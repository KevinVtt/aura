# AURA

Plataforma de chatbot inteligente con IA para atención al cliente y gestión empresarial.

## Arquitectura

```
frontend (React 18) ──REST──> backend (Spring Boot 3.5) ──REST──> ia (FastAPI) ──> Groq API / Ollama
```

Tres servicios independientes que se comunican vía REST:

| Servicio | Tecnología | Puerto |
|----------|-----------|--------|
| **frontend** | React 18 (CRA) | `:3000` (dev) / `:80` (prod) |
| **backend** | Spring Boot 3.5 + Java 21 + Maven | `:8080` |
| **ia** | FastAPI + Uvicorn | `:8000` |
| **ollama** | Ollama (embeddings locales) | `:11434` |
| **db** | PostgreSQL 17 | `:5432` |

## Migración: de repos separados a monorepo

### Situación anterior

Cada servicio vivía en su propio repositorio de GitHub:

- `github.com/KevinVtt/aura_frontend` — frontend React
- `github.com/KevinVtt/aura_backend` — backend Spring Boot
- `github.com/KevinVtt/proyecto-aura` — chatbot FastAPI

Esto generaba fricción: coordinación manual entre repos, sin docker-compose centralizado, sin una vista unificada del proyecto.

### Cambios realizados

1. **Monorepo único** — Se unificaron los tres servicios en `github.com/KevinVtt/aura`, eliminando los `.git` individuales para trabajar como un solo proyecto.
2. **Dockerización completa** — Se agregaron `Dockerfile` a cada servicio y un `docker-compose.yml` que orquesta todo el stack (frontend, backend, ia, ollama, postgres).
3. **Configuración centralizada** — Variables de entorno en `.env` raíz, inyectadas a cada servicio según corresponda.
4. **Entorno seguro** — `.env` y `application.properties` en `.gitignore` para evitar filtrar secretos.
5. **Nginx para frontend** — `nginx.conf` para servir el build de React en producción con enrutamiento SPA.
6. **DataInitializer** — Carga inicial de datos en la base de datos al iniciar el backend.
7. **Commits organizados** — 4 commits con conventional commits, uno por servicio.

### Mejoras

| Aspecto | Antes | Ahora |
|---------|-------|-------|
| **Repositorios** | 3 repos separados | 1 monorepo |
| **Despliegue** | Manual, servicio por servicio | `docker compose up -d` |
| **Visibilidad** | Cada repo aislado | Vista completa del proyecto |
| **Secretos** | `.env` commiteado | En `.gitignore` |
| **Frontend prod** | Sin configuración | Nginx con SPA routing |
| **Backend** | Sin Docker | Multi-stage Docker build |
| **Base de datos** | Sin inicialización | DataInitializer automático |

## Quick Start

```bash
git clone https://github.com/KevinVtt/aura.git
cd deploy

# Configurar variables de entorno
cp .env.example .env
# Editar .env con GROQ_API_KEY, DB_PASS, JWT_SECRET

# Iniciar todo el stack
docker compose up -d

# Ver logs
docker compose logs -f

# Detener
docker compose down
```

## Estructura del proyecto

```
deploy/
├── frontend/aura-frontend/   # React 18 (CRA)
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
├── backend/project-aura/     # Spring Boot 3.5 + Java 21
│   ├── Dockerfile
│   └── src/main/java/com/project/aura/
├── aura_chatbot/             # FastAPI + Groq + Ollama
│   ├── Dockerfile
│   ├── main.py
│   ├── chat_service.py
│   └── llm_service.py
├── docker-compose.yml        # Orquestación completa
├── AGENTS.md                 # Contexto del proyecto
└── .gitignore
```

## Stack técnico

- **Frontend:** React 18, react-scripts (CRA), Axios, JWT auth
- **Backend:** Spring Boot 3.5, JPA/Hibernate, PostgreSQL, JJWT 0.11.5
- **IA:** FastAPI, Groq API (llama-3.3-70b-versatile), Ollama (nomic-embed-text), ChromaDB
- **Infra:** Docker Compose, Nginx, PostgreSQL 17

## Licencia

Proyecto académico.
