# Aura Backend

Backend del sistema Aura — Asistente IA para PyMEs.
Desarrollado con Spring Boot 3.2 y Java 21.

---

## Tecnologías

- Java 21
- Spring Boot 3.2
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
│   └── RestTemplateConfig.java   ← bean de RestTemplate
├── controllers/
│   ├── AuthController.java       ← login
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
│   └── FastAPIClient.java        ← cliente HTTP hacia FastAPI
└── AuraApplication.java
```

---

## Requisitos previos

- Java 21
- Maven 3.9 o superior
- PostgreSQL 15 o superior
- FastAPI corriendo en localhost:8000

---

## Configuración de la base de datos

Crear la base de datos en PostgreSQL:

```sql
CREATE DATABASE aura_db;
```

---

## Variables de configuración

Editá `src/main/resources/application.properties`:

```properties
# Base de datos
spring.datasource.url=jdbc:postgresql://localhost:5432/aura_db
spring.datasource.username=postgres
spring.datasource.password=TU_PASSWORD

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# JWT
jwt.secret=TU_CLAVE_SECRETA_MINIMO_32_CARACTERES
jwt.expiration=86400000

# FastAPI
fastapi.url=http://localhost:8000

# Multipart
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

---

## Correr el proyecto

```bash
./mvnw spring-boot:run
```

O desde el IDE ejecutando `AuraApplication.java`.

El servidor corre en http://localhost:8080

---

## Crear el primer usuario ADMIN

Con el servidor corriendo, enviá este request desde Postman:

```
POST http://localhost:8080/auth/register
Content-Type: application/json

{
    "nombre": "Admin",
    "password": "12345678",
    "rol": "ADMIN",
    "contacto": {
        "email": "admin@empresa.com",
        "telefono": "1138443563"
    }
}
```

Una vez creado el primer ADMIN, los demás usuarios
se crean desde el panel de administración del frontend.

---

## Endpoints disponibles

### Autenticación (público)
| Método | Ruta           | Descripción        |
|--------|----------------|--------------------|
| POST   | /auth/login    | Iniciar sesión     |

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

**Error al conectar con PostgreSQL** → Verificá usuario, contraseña y que el servicio esté corriendo.

**Error 403 en todos los endpoints** → Verificá que el token JWT esté incluido en el header.

**El chat no responde** → Verificá que FastAPI esté corriendo en `localhost:8000`.

**Las tablas no se crean** → Verificá que `spring.jpa.hibernate.ddl-auto=update` esté en `application.properties`.
