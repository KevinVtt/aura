# Aura Frontend

Interfaz web del sistema Aura — Asistente IA para PyMEs.
Desarrollado con React 18.

> Este servicio forma parte del monorepo **AURA**.  
> Para levantar todo el stack: `docker compose up -d` en la raíz del proyecto.

---

## Tecnologías

- React 18
- React Router DOM 6
- Axios
- CSS vanilla con variables

---

## Estructura del proyecto

```
src/
├── api/
│   └── axios.js              ← config de axios + JWT automático
├── components/
│   ├── Navbar.jsx            ← barra de navegación
│   └── ProtectedRoute.jsx    ← protección de rutas por rol
├── context/
│   └── AuthContext.jsx       ← manejo de sesión global
└── pages/
    ├── Login.jsx             ← inicio de sesión
    ├── AdminPanel.jsx        ← gestión de documentos y usuarios
    └── Chat.jsx              ← chat con el asistente IA
```

---

## Requisitos previos

### Opción 1 — Docker (recomendada)
- Docker y Docker Compose instalados
- Ejecutar `docker compose up -d` desde la raíz del monorepo

### Opción 2 — Desarrollo local
- Node.js 18 o superior
- Backend en `http://localhost:8080`
- FastAPI en `http://localhost:8000`

---

## Instalación

```bash
npm install
```

---

## Correr el proyecto

### Con Docker
El frontend se sirve vía Nginx en `http://localhost:3000`.

### Desarrollo local
```bash
npm start
```

Se abre automáticamente en `http://localhost:3000`.

---

## Usuarios del sistema

| Rol      | Acceso                          |
|----------|---------------------------------|
| ADMIN    | Panel de administración + Chat  |
| EMPLEADO | Solo Chat                       |

El primer usuario ADMIN se crea automáticamente al iniciar el backend
(usuario: `Admin`, contraseña: `admin123`).
A partir de ahí, el ADMIN crea los demás usuarios desde el panel.

---

## Rutas disponibles

| Ruta   | Acceso         | Descripción                      |
|--------|----------------|----------------------------------|
| /login | Público        | Inicio de sesión                 |
| /admin | Solo ADMIN     | Gestión de documentos y usuarios |
| /chat  | ADMIN/EMPLEADO | Chat con el asistente IA         |

---

## Conexión con el backend

El frontend se conecta al backend Spring Boot. La URL se configura mediante
la variable de entorno `REACT_APP_API_URL`:

| Entorno        | URL                              |
|----------------|----------------------------------|
| Docker         | `http://backend:8080`            |
| Local          | `http://localhost:8080`          |

El token JWT se agrega automáticamente en cada request
una vez que el usuario inicia sesión.

---

## Solución de problemas

**Pantalla en blanco al abrir** → Verificá que el servicio esté corriendo (`npm start` o `docker compose ps`).

**Error 401 en todas las requests** → El token expiró, iniciá sesión de nuevo.

**Error CORS** → Verificá que Spring Boot tenga CORS habilitado para el origen correspondiente.

**El chat no responde** → Verificá que el servicio IA esté corriendo (`docker compose ps`).
