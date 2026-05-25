# Aura Frontend

Interfaz web del sistema Aura — Asistente IA para PyMEs.
Desarrollado con React 18.

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

- Node.js 18 o superior
- Spring Boot corriendo en localhost:8080
- FastAPI corriendo en localhost:8000

---

## Instalación

```bash
npm install
```

---

## Correr el proyecto

```bash
npm start
```

Se abre automáticamente en http://localhost:3000

---

## Usuarios del sistema

| Rol      | Acceso                          |
|----------|---------------------------------|
| ADMIN    | Panel de administración + Chat  |
| EMPLEADO | Solo Chat                       |

El primer usuario ADMIN debe crearse directamente desde Postman
o desde el endpoint `/auth/register` antes de deshabilitar el registro.
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

El frontend se conecta a Spring Boot en `http://localhost:8080`.
Para cambiarlo, modificá `src/api/axios.js`:

```javascript
const api = axios.create({
  baseURL: 'http://localhost:8080',
});
```

El token JWT se agrega automáticamente en cada request
una vez que el usuario inicia sesión.

---

## Solución de problemas

**Pantalla en blanco al abrir** → Verificá que `npm start` esté corriendo.

**Error 401 en todas las requests** → El token expiró, iniciá sesión de nuevo.

**Error CORS** → Verificá que Spring Boot tenga CORS habilitado para `localhost:3000`.

**El chat no responde** → Verificá que FastAPI esté corriendo en `localhost:8000`.
