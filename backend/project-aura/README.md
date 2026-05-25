# Aura Backend

Backend del sistema Aura — Asistente IA para PyMEs.
Desarrollado con Spring Boot 3.5 y Java 21.

> Este servicio forma parte del monorepo **AURA**.  
> Para levantar todo el stack: `docker compose up -d` en la raíz del proyecto.

---

## Tecnologías

- Java 21
- Spring Boot 3.5
- Spring Security + JWT
- Spring Data JPA / Hibernate
- PostgreSQL
- WebSocket (STOMP)
- Lombok
- Maven

---

## Estructura del proyecto

```
src/main/java/com/project/aura/
├── config/
│   ├── SecurityConfig.java       ← configuración de Spring Security
│   ├── WebSocketConfig.java      ← configuración de WebSocket
│   ├── RestTemplateConfig.java   ← bean de RestTemplate
│   └── DataInitializer.java      ← carga inicial de datos
├── controllers/
│   ├── AuthController.java       ← login y registro
│   ├── UsuarioController.java    ← gestión de usuarios (solo ADMIN)
│   ├── DocumentoController.java  ← gestión de documentos (solo ADMIN)
│   └── ChatController.java       ← chat y conversaciones
├── dto/
│   ├── LoginRequest.java
│   ├── LoginResponse.java
│   └── PreguntaRequest.java
├── jwt/
│   ├── JwtUtil.java              ← generación y validación de tokens
│   └── JwtFilter.java            ← filtro de autenticación
├── models/
│   ├── Usuario.java
│   ├── Contacto.java
│   ├── Conversacion.java
│   ├── Mensaje.java
│   └── Documento.java
├── repositories/
│   ├── UsuarioRepository.java
│   ├── ContactoRepository.java
│   ├── ConversacionRepository.java
│   ├── MensajeRepository.java
│   └── DocumentoRepository.java
├── security/
│   └── UserDetailsServiceImpl.java
├── services/
│   ├── UsuarioService.java
│   ├── ConversacionService.java
│   ├── DocumentoService.java
│   └── FastAPIClient.java        ← cliente HTTP hacia el servicio IA
└── AuraApplication.java
```

---

## Requisitos previos

### Opción 1 — Docker (recomendada)
- Docker y Docker Compose instalados
- Ejecutar `docker compose up -d` desde la raíz del monorepo

### Opción 2 — Desarrollo local
- Java 21
- Maven 3.9 o superior
- PostgreSQL 15 o superior
- Servicio IA corriendo en `localhost:8000`

---

## Configuración

### Con Docker
Las variables de entorno se inyectan desde el `docker-compose.yml` y el archivo `.env` raíz:

| Variable      | Propósito                          | Docker                           |
|---------------|------------------------------------|----------------------------------|
| `DB_URL`      | URL de conexión a PostgreSQL       | `jdbc:postgresql://db:5432/aura_db` |
| `DB_USER`     | Usuario de base de datos           | `postgres`                       |
| `DB_PASS`     | Contraseña de base de datos        | del `.env` raíz                  |
| `JWT_SECRET`  | Clave secreta para firmar tokens   | del `.env` raíz                  |
| `IA_SERVICE_URL` | URL del servicio IA             | `http://ia:8000`                 |

### Desarrollo local
Copiá `application.properties.example` a `application.properties` y editá los valores:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/aura_db
spring.datasource.username=postgres
spring.datasource.password=TU_PASSWORD
jwt.secret=TU_CLAVE_SECRETA_MINIMO_32_CARACTERES
fastapi.url=http://localhost:8000
```

---

## Correr el proyecto

### Con Docker
```bash
docker compose up -d backend
```

### Desarrollo local
```bash
./mvnw spring-boot:run
```

El servidor corre en `http://localhost:8080`.

---

## Crear el primer usuario ADMIN

El backend incluye un `DataInitializer` que crea automáticamente
un usuario ADMIN al iniciar por primera vez:

| Campo      | Valor           |
|------------|-----------------|
| Usuario    | `Admin`         |
| Contraseña | `admin123`      |
| Email      | `admin@aura.com`|

Una vez iniciado sesión, el ADMIN puede crear más usuarios
desde el panel de administración del frontend.

---

## Endpoints disponibles

### Autenticación (público)
| Método | Ruta             | Descripción        |
|--------|------------------|--------------------|
| POST   | /auth/login      | Iniciar sesión     |
| POST   | /auth/register   | Registrar usuario  |

### Usuarios (solo ADMIN)
| Método | Ruta                    | Descripción           |
|--------|-------------------------|-----------------------|
| GET    | /usuarios               | Listar usuarios       |
| POST   | /usuarios               | Crear usuario         |
| DELETE | /usuarios/{id}          | Desactivar usuario    |
| PATCH  | /usuarios/{id}/activar  | Activar usuario       |

### Documentos (solo ADMIN)
| Método | Ruta                    | Descripción           |
|--------|-------------------------|-----------------------|
| GET    | /documentos             | Listar documentos     |
| POST   | /documentos/subir       | Subir PDF             |
| DELETE | /documentos/{id}        | Eliminar documento    |

### Chat (ADMIN y EMPLEADO)
| Método | Ruta                          | Descripción                  |
|--------|-------------------------------|------------------------------|
| POST   | /chat/iniciar                 | Iniciar conversación         |
| POST   | /chat/{sesionId}/mensaje      | Enviar mensaje               |
| GET    | /chat/{sesionId}/mensajes     | Ver mensajes de la sesión    |
| GET    | /chat/conversaciones          | Listar conversaciones        |
| PATCH  | /chat/{sesionId}/cerrar       | Cerrar conversación          |
| DELETE | /chat/{sesionId}              | Eliminar conversación        |

---

## Autenticación

Todos los endpoints protegidos requieren el token JWT en el header:

```
Authorization: Bearer eyJhbGci...
```

El token se obtiene del endpoint `/auth/login` y expira en 24 horas.

---

## Roles

| Rol      | Permisos                                         |
|----------|--------------------------------------------------|
| ADMIN    | Acceso total — documentos, usuarios y chat       |
| EMPLEADO | Solo chat                                        |

---

## Solución de problemas

**Error al conectar con PostgreSQL** → Verificá credenciales y que el servicio esté corriendo.

**Error 403 en todos los endpoints** → Verificá que el token JWT esté incluido en el header.

**El chat no responde** → Verificá que el servicio IA esté corriendo (`docker compose ps`).

**Las tablas no se crean** → Verificá que `spring.jpa.hibernate.ddl-auto=update` esté configurado.
