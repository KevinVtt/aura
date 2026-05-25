from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import shutil, os, uuid
import document_processor as dp
import chat_service as cs

app = FastAPI(title="Aura Chatbot API")

# Permite llamadas desde el frontend (cualquier origen en desarrollo)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
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
    if not archivo.filename.endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Solo se aceptan archivos PDF.")

    ruta = f"./pdfs/{archivo.filename}"
    with open(ruta, "wb") as f:
        shutil.copyfileobj(archivo.file, f)

    try:
        dp.procesar_pdf(ruta, archivo.filename)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

    return {"mensaje": f"'{archivo.filename}' procesado correctamente."}


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

    resultado = cs.responder(req.sesion_id, req.pregunta)
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
        raise HTTPException(status_code=500, detail=str(e))

    return {"mensaje": f"'{nombre_doc}' eliminado correctamente."}
