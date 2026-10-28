"""Unit tests for document_processor.py — PDF text extraction, chunking with
overlap, and ChromaDB operations. ChromaDB and Ollama are mocked; the only real
filesystem writes go to the temp hermetic environment (./pdfs, ./chroma_db)."""

import os

import pytest
from unittest.mock import MagicMock


# ── _dividir_en_chunks ────────────────────────────────────────────────────────

def test_dividir_en_chunks_texto_corto(aura_modules):
    dp = aura_modules.document_processor
    texto = " ".join(["palabra"] * 100)
    chunks = dp._dividir_en_chunks(texto)
    assert len(chunks) == 1
    assert chunks[0] == texto


def test_dividir_en_chunks_texto_largo_con_overlap(aura_modules):
    dp = aura_modules.document_processor
    texto = " ".join(f"palabra{i}" for i in range(1200))
    chunks = dp._dividir_en_chunks(texto)
    assert len(chunks) == 3
    assert len(chunks[0].split()) == 500
    assert len(chunks[1].split()) == 500
    assert len(chunks[2].split()) == 300
    # El segundo chunk arranca en la palabra 450 (overlap de 50)
    assert chunks[1].startswith("palabra450")
    assert chunks[2].startswith("palabra900")


def test_dividir_en_chunks_vacio(aura_modules):
    dp = aura_modules.document_processor
    assert dp._dividir_en_chunks("") == []


# ── _extraer_texto ────────────────────────────────────────────────────────────

def test_extraer_texto_concatena_paginas(aura_modules, monkeypatch):
    dp = aura_modules.document_processor

    class FakePage:
        def __init__(self, texto):
            self._texto = texto

        def extract_text(self):
            return self._texto

    class FakeReader:
        def __init__(self, ruta):
            self.pages = [FakePage("Hola "), FakePage(None), FakePage("mundo")]

    monkeypatch.setattr(dp, "PdfReader", FakeReader)
    assert dp._extraer_texto("cualquier.pdf") == "Hola mundo"


def test_extraer_texto_docx_incluye_parrafos_y_tablas(tmp_path, aura_modules):
    from docx import Document as DocxDocument

    dp = aura_modules.document_processor
    ruta = tmp_path / "politicas.docx"
    documento = DocxDocument()
    documento.add_paragraph("Licencia anual de 21 días.")
    tabla = documento.add_table(rows=1, cols=2)
    tabla.rows[0].cells[0].text = "Área"
    tabla.rows[0].cells[1].text = "Tope"
    documento.save(ruta)

    texto = dp._extraer_texto(str(ruta))

    assert "Licencia anual de 21 días." in texto
    assert "Área | Tope" in texto
    assert texto.index("Licencia") < texto.index("Área")


def test_extraer_texto_xlsx_una_fila_por_linea(tmp_path, aura_modules):
    from openpyxl import Workbook

    dp = aura_modules.document_processor
    ruta = tmp_path / "sueldos.xlsx"
    libro = Workbook()
    hoja = libro.active
    hoja.title = "Sueldos"
    hoja.append(["Puesto", "Sueldo"])
    hoja.append([None, None])          # fila vacía: no debe aparecer
    hoja.append(["Analista", 1500])
    libro.save(ruta)

    texto = dp._extraer_texto(str(ruta))

    assert "Hoja: Sueldos" in texto
    assert "Puesto | Sueldo" in texto
    assert "Analista | 1500" in texto
    assert "\n\n" not in texto


def test_extraer_texto_formato_viejo_pide_convertir(aura_modules):
    dp = aura_modules.document_processor
    with pytest.raises(ValueError, match="Guardalos como .docx o .xlsx"):
        dp._extraer_texto("contrato.doc")


def test_extraer_texto_archivo_danado_lanza_valueerror(tmp_path, aura_modules):
    dp = aura_modules.document_processor
    ruta = tmp_path / "roto.docx"
    ruta.write_bytes(b"esto no es un docx")

    with pytest.raises(ValueError, match="dañado"):
        dp.procesar_documento(str(ruta), "roto.docx")


# ── procesar_pdf ─────────────────────────────────────────────────────────────

def test_procesar_pdf_guarda_chunks_en_chroma(aura_modules, monkeypatch):
    dp = aura_modules.document_processor
    monkeypatch.setattr(dp, "_extraer_texto", lambda ruta: "hola mundo")
    monkeypatch.setattr(dp, "generar_embeddings", lambda textos: [[0.1, 0.2] for _ in textos])
    coleccion = MagicMock()
    monkeypatch.setattr(dp, "coleccion", coleccion)

    dp.procesar_documento("./pdfs/doc.pdf", "doc.pdf")

    # una version anterior del mismo archivo se borra antes de guardar la nueva
    coleccion.delete.assert_called_once_with(where={"fuente": "doc.pdf"})
    coleccion.add.assert_called_once_with(
        ids=["doc.pdf_chunk_0"],
        embeddings=[[0.1, 0.2]],
        documents=["hola mundo"],
        metadatas=[{"fuente": "doc.pdf", "chunk_index": 0}],
    )


def test_procesar_pdf_pide_embeddings_en_lotes(aura_modules, monkeypatch):
    """Un PDF largo no puede hacer un pedido a Ollama por chunk: tarda minutos."""
    dp = aura_modules.document_processor
    monkeypatch.setattr(dp, "LOTE", 2)
    monkeypatch.setattr(
        dp, "_extraer_texto",
        lambda ruta: " ".join(f"palabra{i}" for i in range(dp.CHUNK_SIZE * 4)),
    )
    pedidos = []

    def fake_embeddings(textos):
        pedidos.append(len(textos))
        return [[0.5] for _ in textos]

    monkeypatch.setattr(dp, "generar_embeddings", fake_embeddings)
    coleccion = MagicMock()
    monkeypatch.setattr(dp, "coleccion", coleccion)

    dp.procesar_documento("x.pdf", "x.pdf")

    assert pedidos == [2, 2, 1]
    assert coleccion.add.call_count == 3


def test_procesar_documento_texto_vacio_lanza_valueerror(aura_modules, monkeypatch):
    dp = aura_modules.document_processor
    monkeypatch.setattr(dp, "_extraer_texto", lambda ruta: "   ")
    with pytest.raises(ValueError, match="no tiene texto"):
        dp.procesar_documento("x.pdf", "x.pdf")


# ── buscar_chunks_relevantes ─────────────────────────────────────────────────

def test_buscar_chunks_relevantes(aura_modules, monkeypatch):
    dp = aura_modules.document_processor
    monkeypatch.setattr(dp, "generar_embedding", lambda texto: [0.5])
    coleccion = MagicMock()
    coleccion.query.return_value = {
        "documents": [["texto1", "texto2"]],
        "metadatas": [[{"fuente": "a.pdf"}, {"fuente": "b.pdf"}]],
        "distances": [[0.1, 0.2]],
    }
    monkeypatch.setattr(dp, "coleccion", coleccion)

    resultado = dp.buscar_chunks_relevantes("pregunta", top_k=2)

    assert resultado == [
        {"texto": "texto1", "fuente": "a.pdf"},
        {"texto": "texto2", "fuente": "b.pdf"},
    ]
    coleccion.query.assert_called_once_with(
        query_embeddings=[[0.5]],
        n_results=2,
        include=["documents", "metadatas", "distances"],
    )


def test_buscar_chunks_relevantes_no_descarta_por_distancia(aura_modules, monkeypatch):
    """Los k mas cercanos llegan al prompt aunque la distancia sea alta.

    Medido con nomic-embed-text sobre un PDF real: una pregunta cuyo contenido
    SI esta en el documento dio distancias 184-333, y una pregunta ajena
    298-365. Los rangos se solapan, asi que un umbral fijo no separa "es del
    documento" de "no lo es": solo borraba respuestas legitimas y el bot
    contestaba "No tengo informacion" a preguntas que si podia responder.
    El prompt ya le indica al modelo cuando decir "No tengo informacion"."""
    dp = aura_modules.document_processor
    monkeypatch.setattr(dp, "generar_embedding", lambda texto: [0.5])
    coleccion = MagicMock()
    coleccion.query.return_value = {
        "documents": [["cerca", "lejos"]],
        "metadatas": [[{"fuente": "a.pdf"}, {"fuente": "b.pdf"}]],
        "distances": [[150.0, 400.0]],
    }
    monkeypatch.setattr(dp, "coleccion", coleccion)

    resultado = dp.buscar_chunks_relevantes("pregunta", top_k=2)

    assert resultado == [
        {"texto": "cerca", "fuente": "a.pdf"},
        {"texto": "lejos", "fuente": "b.pdf"},
    ]


# ── listar_documentos ────────────────────────────────────────────────────────

def test_listar_documentos_devuelve_fuentes_unicas_ordenadas(aura_modules, monkeypatch):
    dp = aura_modules.document_processor
    coleccion = MagicMock()
    coleccion.get.return_value = {
        "metadatas": [{"fuente": "b.pdf"}, {"fuente": "a.pdf"}, {"fuente": "b.pdf"}]
    }
    monkeypatch.setattr(dp, "coleccion", coleccion)

    assert dp.listar_documentos() == ["a.pdf", "b.pdf"]


# ── eliminar_documento ───────────────────────────────────────────────────────

def test_eliminar_documento_elimina_ids_y_archivo(aura_modules, monkeypatch):
    dp = aura_modules.document_processor
    os.makedirs("./pdfs", exist_ok=True)
    with open("./pdfs/a.pdf", "w") as f:
        f.write("x")
    coleccion = MagicMock()
    coleccion.get.return_value = {
        "ids": ["a_chunk_0", "b_chunk_0"],
        "metadatas": [{"fuente": "a.pdf"}, {"fuente": "b.pdf"}],
    }
    monkeypatch.setattr(dp, "coleccion", coleccion)

    dp.eliminar_documento("a.pdf")

    coleccion.delete.assert_called_once_with(ids=["a_chunk_0"])
    assert not os.path.exists("./pdfs/a.pdf")


def test_eliminar_documento_sanitiza_path_traversal(aura_modules, monkeypatch):
    """Un nombre con ``../`` se reduce a su basename: borra ./pdfs/a.pdf y
    nunca un archivo fuera de ./pdfs/."""
    dp = aura_modules.document_processor
    os.makedirs("./pdfs", exist_ok=True)
    with open("./pdfs/a.pdf", "w") as f:
        f.write("x")
    coleccion = MagicMock()
    coleccion.get.return_value = {
        "ids": ["a_chunk_0"],
        "metadatas": [{"fuente": "a.pdf"}],
    }
    monkeypatch.setattr(dp, "coleccion", coleccion)

    dp.eliminar_documento("../a.pdf")

    coleccion.delete.assert_called_once_with(ids=["a_chunk_0"])
    assert not os.path.exists("./pdfs/a.pdf")


def test_eliminar_documento_no_encontrado_lanza_valueerror(aura_modules, monkeypatch):
    dp = aura_modules.document_processor
    coleccion = MagicMock()
    coleccion.get.return_value = {
        "ids": ["b_chunk_0"],
        "metadatas": [{"fuente": "b.pdf"}],
    }
    monkeypatch.setattr(dp, "coleccion", coleccion)

    with pytest.raises(ValueError, match="No se encontró"):
        dp.eliminar_documento("a.pdf")