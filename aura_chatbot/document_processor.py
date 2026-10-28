import chromadb,os
from pypdf import PdfReader
from docx import Document as DocxDocument
from docx.table import Table
from docx.text.paragraph import Paragraph
from openpyxl import load_workbook
from llm_service import generar_embedding, generar_embeddings

# Base de datos vectorial local (se guarda en ./chroma_db/)
chroma_client = chromadb.PersistentClient(path="./chroma_db")
coleccion = chroma_client.get_or_create_collection(name="documentos_pyme")

CHUNK_SIZE = 500   # palabras por chunk
OVERLAP    = 50    # palabras que se repiten entre chunks
LOTE       = 32    # chunks por pedido de embeddings

# Topes para que un Excel enorme no se coma la memoria del servicio.
MAX_CARACTERES = int(os.environ.get("MAX_CARACTERES_DOC", "2000000"))
MAX_FILAS_XLSX = int(os.environ.get("MAX_FILAS_XLSX", "20000"))


def _extraer_texto_pdf(ruta: str) -> str:
    """Lee todas las páginas del PDF y devuelve el texto completo."""
    reader = PdfReader(ruta)
    texto = ""
    for pagina in reader.pages:
        texto += pagina.extract_text() or ""
    return texto


def _extraer_texto_docx(ruta: str) -> str:
    """Párrafos y tablas de un .docx, en el orden en que aparecen."""
    documento = DocxDocument(ruta)
    partes = []
    for hijo in documento.element.body.iterchildren():
        if hijo.tag.endswith("}p"):
            texto = Paragraph(hijo, documento).text.strip()
            if texto:
                partes.append(texto)
        elif hijo.tag.endswith("}tbl"):
            for fila in Table(hijo, documento).rows:
                celdas = [c.text.strip() for c in fila.cells]
                if any(celdas):
                    partes.append(" | ".join(celdas))
    return "\n".join(partes)


def _extraer_texto_xlsx(ruta: str) -> str:
    """Cada hoja como texto, una fila por línea y las celdas separadas por ' | '."""
    libro = load_workbook(ruta, read_only=True, data_only=True)
    partes = []
    filas = 0
    try:
        for hoja in libro.worksheets:
            partes.append(f"Hoja: {hoja.title}")
            for fila in hoja.iter_rows(values_only=True):
                celdas = [str(c).strip() for c in fila if c is not None and str(c).strip()]
                if not celdas:
                    continue
                if filas >= MAX_FILAS_XLSX:
                    print(f"   [aviso] corte en {MAX_FILAS_XLSX} filas: el resto del Excel no se indexó")
                    return "\n".join(partes)
                filas += 1
                partes.append(" | ".join(celdas))
    finally:
        libro.close()
    return "\n".join(partes)


def _extraer_texto(ruta: str) -> str:
    """Devuelve el texto plano según la extensión del archivo."""
    extension = os.path.splitext(ruta)[1].lower()
    if extension == ".pdf":
        return _extraer_texto_pdf(ruta)
    if extension == ".docx":
        return _extraer_texto_docx(ruta)
    if extension == ".xlsx":
        return _extraer_texto_xlsx(ruta)
    if extension in (".doc", ".xls"):
        raise ValueError(
            "Los archivos .doc y .xls (formatos viejos de Office) no se pueden leer. "
            "Guardalos como .docx o .xlsx y subilos de nuevo."
        )
    raise ValueError(f"Formato no soportado ({extension or 'sin extensión'}). Se aceptan PDF, .docx y .xlsx.")

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


def procesar_documento(ruta: str, nombre_doc: str):
    """
    Pipeline completo:
    1. Extrae texto (PDF, .docx o .xlsx)
    2. Divide en chunks
    3. Genera embedding de cada chunk
    4. Guarda en ChromaDB
    """
    print(f"\n[doc] Procesando: {nombre_doc}")
    try:
        texto = _extraer_texto(ruta)
    except ValueError:
        raise
    except Exception as e:
        print(f"   [ERROR] no se pudo leer {nombre_doc}: {e}")
        raise ValueError("No se pudo leer el archivo: puede estar dañado o no ser un archivo válido.")

    if not texto.strip():
        raise ValueError(
            "El documento no tiene texto que se pueda leer. Si es un PDF, "
            "probablemente esté escaneado: pasalo antes por un programa de OCR."
        )

    if len(texto) > MAX_CARACTERES:
        print(f"   [aviso] texto recortado a {MAX_CARACTERES} caracteres")
        texto = texto[:MAX_CARACTERES]

    chunks = _dividir_en_chunks(texto)
    print(f"   -> {len(chunks)} chunks generados")

    # Resubir el mismo archivo debe reemplazarlo: un add con ids ya usados se ignora
    # en silencio y la IA seguiria respondiendo con el texto viejo.
    coleccion.delete(where={"fuente": nombre_doc})

    for inicio in range(0, len(chunks), LOTE):
        lote = chunks[inicio:inicio + LOTE]
        embeddings = generar_embeddings(lote)
        coleccion.add(
            ids=[f"{nombre_doc}_chunk_{inicio + i}" for i in range(len(lote))],
            embeddings=embeddings,
            documents=lote,
            metadatas=[
                {"fuente": nombre_doc, "chunk_index": inicio + i}
                for i in range(len(lote))
            ]
        )
        print(f"   -> {inicio + len(lote)}/{len(chunks)} chunks guardados")

    print(f"   OK: guardado en ChromaDB")


def buscar_chunks_relevantes(pregunta: str, top_k: int = 3) -> list[dict]:
    """Devuelve los `top_k` fragmentos mas cercanos a la pregunta.

    No filtra por distancia, a proposito. Hubo un umbral fijo (250) y hubo que
    sacarlo: medido con nomic-embed-text sobre un PDF real de 2 paginas, una
    pregunta cuyo contenido SI esta en el documento dio 184-333, y una pregunta
    ajena 298-365. Los rangos se solapan, asi que ningun umbral fijo separa "es
    del documento" de "no lo es": lo unico que hacia era borrar respuestas
    legitimas y que el bot contestara "No tengo informacion" a preguntas que si
    sabia responder (probado: leidas las 4 preguntas de contenido con los
    fragmentos sin filtrar, las respondio bien, y rechazo las 2 ajenas). Quien
    decide si los fragmentos alcanzan es el modelo, con la instruccion del
    prompt de no inventar y decir "No tengo informacion" cuando no alcanzan.
    Probado tambien achicar los fragmentos (hasta 60 palabras): los rangos
    siguen solapandose, asi que no es cuestion del tamano del chunk.
    """
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
        print(f"Distancia: {dist:.1f} | Fuente: {meta['fuente']}")
        print(f"Texto: {doc[:150]}")
        print("---")

    return [
        {"texto": doc, "fuente": meta["fuente"]}
        for doc, meta in zip(
            resultados["documents"][0], resultados["metadatas"][0]
        )
    ]


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
