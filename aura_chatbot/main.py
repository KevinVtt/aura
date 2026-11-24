from fastapi import FastAPI, UploadFile, File, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel
import secrets, shutil, os, uuid
import sys

# Windows: stdout es cp1252 y los simbolos que imprime document_processor
# (flecha, tilde de check) tiraban UnicodeEncodeError y rompian la subida.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

import document_processor as dp
import chat_service as cs

app = FastAPI(title="Aura Chatbot API")

# Si esta definido, solo el backend (que manda X-IA-Token) puede usar la API.
# Vacio = sin control, para desarrollo local.
IA_TOKEN = os.environ.get("IA_TOKEN", "")


@app.middleware("http")
async def exigir_token(request: Request, call_next):
    if IA_TOKEN and request.url.path != "/":
        recibido = request.headers.get("x-ia-token", "")
        if not secrets.compare_digest(recibido, IA_TOKEN):
            return JSONResponse(status_code=401, content={"detail": "No autorizado."})
    return await call_next(request)

# Orígenes permitidos (coma separados). Default: frontend local.
ALLOWED_ORIGINS = [
    o.strip()
    for o in os.environ.get(
        "ALLOWED_ORIGINS", "http://localhost:3000,http://localhost"
    ).split(",")
    if o.strip()
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_methods=["*"],
    allow_headers=["*"],
)

os.makedirs("./pdfs", exist_ok=True)


# ── Schemas ────────────────────────────────────────────────────────

class PreguntaRequest(BaseModel):
    sesion_id: str
    pregunta: str

class NuevaSesionResponse(BaseModel):
    sesion_id: str

# ── Endpoints ──────────────────────────────────────────────────────

@app.get("/")
def raiz():
    return {"estado": "ok", "mensaje": "Aura Chatbot corriendo ✓"}


@app.post("/sesion/nueva", response_model=NuevaSesionResponse)
def nueva_sesion():
    """Crea un ID de sesión único para un nuevo usuario."""
    return {"sesion_id": str(uuid.uuid4())}


@app.post("/documentos/subir")
def subir_documento(archivo: UploadFile = File(...)):
    """
    Recibe un PDF, Word o Excel, lo guarda y lo procesa (chunking + embeddings → ChromaDB).
    """
    nombre = os.path.basename(archivo.filename or "")
    if not nombre.lower().endswith((".pdf", ".docx", ".xlsx")):
        raise HTTPException(status_code=400, detail="Solo se aceptan archivos PDF, Word (.docx) o Excel (.xlsx).")

    ruta = f"./pdfs/{nombre}"
    with open(ruta, "wb") as f:
        shutil.copyfileobj(archivo.file, f)

    if os.path.getsize(ruta) == 0:
        os.remove(ruta)
        raise HTTPException(status_code=400, detail="El archivo está vacío.")

    try:
        aviso = dp.procesar_documento(ruta, nombre)
    except ValueError as e:
        # Mensaje controlado de procesar_documento (sin texto, formato viejo, dañado): seguro de exponer.
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        print(f"   [ERROR] procesar_documento falló: {e}")
        raise HTTPException(status_code=500, detail="No se pudo procesar el documento.")

    mensaje = f"'{nombre}' procesado correctamente."
    if aviso:
        mensaje += " " + aviso
    return {"mensaje": mensaje}


@app.get("/documentos")
def listar_documentos():
    """Lista los documentos cargados en ChromaDB."""
    return {"documentos": dp.listar_documentos()}


@app.post("/chat")
def chat(req: PreguntaRequest):
    """
    Recibe una pregunta con su sesion_id.
    Devuelve la respuesta, fuentes usadas e historial completo.
    """
    if not req.pregunta.strip():
        raise HTTPException(status_code=400, detail="La pregunta no puede estar vacía.")

    try:
        resultado = cs.responder(req.sesion_id, req.pregunta)
    except HTTPException:
        raise
    except Exception as e:
        print(f"   [ERROR] responder falló: {e}")
        raise HTTPException(status_code=502, detail="El servicio de IA no está disponible.")

    return resultado


@app.get("/chat/historial/{sesion_id}")
def ver_historial(sesion_id: str):
    return {"historial": cs.obtener_historial(sesion_id)}


@app.delete("/chat/historial/{sesion_id}")
def limpiar_historial(sesion_id: str):
    cs.limpiar_historial(sesion_id)
    return {"mensaje": "Historial limpiado."}

@app.delete("/documentos/{nombre_doc}")
def eliminar_documento(nombre_doc: str):
    try:
        dp.eliminar_documento(nombre_doc)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        print(f"   [ERROR] eliminar_documento falló: {e}")
        raise HTTPException(status_code=500, detail="No se pudo eliminar el documento.")

    return {"mensaje": f"'{nombre_doc}' eliminado correctamente."}
