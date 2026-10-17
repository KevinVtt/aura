from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import shutil, os, uuid
import document_processor as dp
import chat_service as cs

app = FastAPI(title="Aura Chatbot API")

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
async def subir_documento(archivo: UploadFile = File(...)):
    """
    Recibe un PDF, lo guarda y lo procesa (chunking + embeddings → ChromaDB).
    """
    nombre = os.path.basename(archivo.filename or "")
    if not nombre.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Solo se aceptan archivos PDF.")

    ruta = f"./pdfs/{nombre}"
    with open(ruta, "wb") as f:
        shutil.copyfileobj(archivo.file, f)

    try:
        dp.procesar_pdf(ruta, nombre)
    except ValueError as e:
        # Mensaje controlado de procesar_pdf (PDF sin texto extraíble): seguro de exponer.
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        print(f"   [ERROR] procesar_pdf falló: {e}")
        raise HTTPException(status_code=500, detail="No se pudo procesar el PDF.")

    return {"mensaje": f"'{nombre}' procesado correctamente."}


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
