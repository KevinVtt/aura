import chromadb,os
from pypdf import PdfReader
from llm_service import generar_embedding

# Base de datos vectorial local (se guarda en ./chroma_db/)
chroma_client = chromadb.PersistentClient(path="./chroma_db")
coleccion = chroma_client.get_or_create_collection(name="documentos_pyme")

CHUNK_SIZE = 500   # palabras por chunk
OVERLAP    = 50    # palabras que se repiten entre chunks

# Distancia L2 maxima para aceptar un fragmento como contexto (medido con
# nomic-embed-text: una pregunta que si corresponde al documento dio 131-168;
# una pregunta ajena, 294-384). Sin umbral, 2 fragmentos que no venian al caso
# inflaban el prompt a 4662 caracteres.
# Si se cambia EMBED_MODEL hay que recalibrar este numero.
MAX_DISTANCIA = 250


def _extraer_texto(ruta_pdf: str) -> str:
    """Lee todas las páginas del PDF y devuelve el texto completo."""
    reader = PdfReader(ruta_pdf)
    texto = ""
    for pagina in reader.pages:
        texto += pagina.extract_text() or ""
    return texto

# Se divide en chunks con solapamiento para mejorar la coherencia de los fragmentos y evitar cortar ideas a la mitad.
def _dividir_en_chunks(texto: str) -> list[str]:
    """
    Divide el texto en fragmentos de CHUNK_SIZE palabras
    con OVERLAP palabras de superposición entre ellos.
    """
    palabras = texto.split()
    chunks = []
    inicio = 0
    while inicio < len(palabras):
        fin = inicio + CHUNK_SIZE 
        chunk = " ".join(palabras[inicio:fin])
        chunks.append(chunk) 
        inicio += CHUNK_SIZE - OVERLAP  # avanza restando el overlap 
    return chunks


def procesar_pdf(ruta_pdf: str, nombre_doc: str):
    """
    Pipeline completo:
    1. Extrae texto del PDF
    2. Divide en chunks
    3. Genera embedding de cada chunk
    4. Guarda en ChromaDB
    """
    print(f"\n[doc] Procesando: {nombre_doc}")
    texto = _extraer_texto(ruta_pdf)

    if not texto.strip():
        raise ValueError("No se pudo extraer texto del PDF.")

    chunks = _dividir_en_chunks(texto)
    print(f"   -> {len(chunks)} chunks generados")

    # Resubir el mismo archivo debe reemplazarlo: un add con ids ya usados se ignora
    # en silencio y la IA seguiria respondiendo con el texto viejo.
    coleccion.delete(where={"fuente": nombre_doc})

    # shortcut: one embedding call per chunk, batch when the ollama batch API is adopted
    for i, chunk in enumerate(chunks):
        embedding = generar_embedding(chunk)
        coleccion.add(
            ids=[f"{nombre_doc}_chunk_{i}"],
            embeddings=[embedding],
            documents=[chunk],
            metadatas=[{"fuente": nombre_doc, "chunk_index": i}]
        )

    print(f"   OK: guardado en ChromaDB")


def buscar_chunks_relevantes(pregunta: str, top_k: int = 3) -> list[dict]:
    vector_pregunta = generar_embedding(pregunta)
    resultados = coleccion.query(
        query_embeddings=[vector_pregunta],
        n_results=top_k,
        include=["documents", "metadatas", "distances"]
    )

    # Solo los fragmentos que hablan del tema. Chroma siempre devuelve los k mas
    # cercanos aunque no tengan nada que ver, y el modelo contesta sobre ellos.
    relevantes = [
        (doc, meta, dist)
        for doc, meta, dist in zip(
            resultados["documents"][0],
            resultados["metadatas"][0],
            resultados["distances"][0]
        )
        if dist <= MAX_DISTANCIA
    ]

    print("=== CHUNKS RECUPERADOS ===")
    for doc, meta, dist in relevantes:
        print(f"Distancia: {dist:.1f} | Fuente: {meta['fuente']}")
        print(f"Texto: {doc[:150]}")
        print("---")
    if not relevantes:
        print(f"(ninguno por debajo de {MAX_DISTANCIA}: se responde sin contexto)")

    return [{"texto": doc, "fuente": meta["fuente"]} for doc, meta, _ in relevantes]


def listar_documentos() -> list[str]:
    """Devuelve los nombres únicos de documentos cargados."""
    todos = coleccion.get(include=["metadatas"])
    fuentes = set(m["fuente"] for m in todos["metadatas"])
    return sorted(fuentes)

def eliminar_documento(nombre_doc: str):
    nombre_doc = os.path.basename(nombre_doc)
    todos = coleccion.get(include=["metadatas"])
    ids_a_eliminar = [
        id_
        for id_, meta in zip(todos["ids"], todos["metadatas"])
        if meta["fuente"] == nombre_doc
    ]

    if not ids_a_eliminar:
        raise ValueError(f"No se encontró '{nombre_doc}' en la base de datos.")

    coleccion.delete(ids=ids_a_eliminar)

    ruta = f"./pdfs/{nombre_doc}"
    if os.path.exists(ruta):
        os.remove(ruta)
