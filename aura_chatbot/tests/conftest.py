"""Shared fixtures for the AURA FastAPI test suite.

Hermeticity contract
--------------------
The service modules perform relative-path side effects at IMPORT time:

  - ``chat_service._init_db()``  -> creates ``./data/historial.db``
  - ``document_processor``       -> creates ``./chroma_db/`` (PersistentClient)
  - ``main``                     -> creates ``./pdfs/``

To keep the suite hermetic (never touching the real ``./data``, ``./pdfs`` or
``./chroma_db`` directories), the session fixture below changes the working
directory to a pytest-managed temp dir BEFORE any service module is imported.
Test modules therefore import the service modules inside fixtures — never at
module level.
"""

import os
from types import SimpleNamespace

import pytest


@pytest.fixture(scope="session", autouse=True)
def _hermetic_environment(tmp_path_factory):
    """Redirect all relative-path side effects to a temp dir for the session."""
    env_dir = tmp_path_factory.mktemp("aura_test_env")
    original_cwd = os.getcwd()
    original_key = os.environ.get("GROQ_API_KEY")
    os.chdir(env_dir)
    # Mirror the Dockerfile (`RUN mkdir -p ./pdfs ./chroma_db ./data`): the
    # production code never creates ./data itself — chat_service._init_db()
    # crashes without it (see FAST-01 in the findings report).
    os.makedirs("./data", exist_ok=True)
    # Remove the real key so an accidentally-unmocked generar_respuesta() can
    # never reach the Groq API. Tests that exercise the Groq path patch
    # llm_service.GROQ_API_KEY explicitly.
    os.environ.pop("GROQ_API_KEY", None)
    yield env_dir
    os.chdir(original_cwd)
    if original_key is None:
        os.environ.pop("GROQ_API_KEY", None)
    else:
        os.environ["GROQ_API_KEY"] = original_key


@pytest.fixture(scope="session")
def aura_modules(_hermetic_environment):
    """Import the four service modules AFTER the chdir, so all relative-path
    side effects land in the temp environment."""
    import chat_service
    import document_processor
    import llm_service
    import main

    return SimpleNamespace(
        main=main,
        chat_service=chat_service,
        document_processor=document_processor,
        llm_service=llm_service,
    )


@pytest.fixture(scope="session")
def client(aura_modules):
    """FastAPI TestClient bound to the real app with all external deps mocked
    per-test."""
    from fastapi.testclient import TestClient

    return TestClient(aura_modules.main.app)