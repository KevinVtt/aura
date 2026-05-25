import chromadb,os
from pypdf import PdfReader
from llm_service import generar_embedding

# Base de datos vectorial local (se guarda en ./chroma_db/)
chroma_client = chromadb.PersistentClient(path="./chroma_db")
coleccion = chroma_client.get_or_create_collection(name="documentos_pyme")

CHUNK_SIZE = 500   # palabras por chunk
OVERLAP    = 50    # palabras que se repiten entre chunks


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
    print(f"\n📄 Procesando: {nombre_doc}")
    texto = _extraer_texto(ruta_pdf)

    if not texto.strip():
        raise ValueError("No se pudo extraer texto del PDF.")

    chunks = _dividir_en_chunks(texto)
    print(f"   → {len(chunks)} chunks generados")

    for i, chunk in enumerate(chunks):
        embedding = generar_embedding(chunk)
        coleccion.add(
            ids=[f"{nombre_doc}_chunk_{i}"],
            embeddings=[embedding],
            documents=[chunk],
            metadatas=[{"fuente": nombre_doc, "chunk_index": i}]
        )

    print(f"   ✓ Guardado en ChromaDB")


def buscar_chunks_relevantes(pregunta: str, top_k: int = 3) -> list[dict]:
    vector_pregunta = generar_embedding(pregunta)
    resultados = coleccion.query(
        query_embeddings=[vector_pregunta],
        n_results=top_k,
        include=["documents", "metadatas", "distances"]
    )

    print("=== CHUNKS RECUPERADOS ===")
    for doc, meta, dist in zip(
        resultados["documents"][0],
        resultados["metadatas"][0],
        resultados["distances"][0]
    ):
        print(f"Similitud: {1-dist:.2f} | Fuente: {meta['fuente']}")
        print(f"Texto: {doc[:150]}")
        print("---")

    chunks = []
    for doc, meta in zip(resultados["documents"][0], resultados["metadatas"][0]):
        chunks.append({"texto": doc, "fuente": meta["fuente"]})
    return chunks


def listar_documentos() -> list[str]:
    """Devuelve los nombres únicos de documentos cargados."""
    todos = coleccion.get(include=["metadatas"])
    fuentes = set(m["fuente"] for m in todos["metadatas"])
    return sorted(fuentes)

def eliminar_documento(nombre_doc: str):
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
