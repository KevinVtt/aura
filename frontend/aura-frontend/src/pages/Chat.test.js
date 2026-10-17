import { act } from 'react';
import { createRoot } from 'react-dom/client';
import Chat from './Chat';

jest.mock('../api/axios', () => ({
  get: jest.fn(),
  post: jest.fn(),
  patch: jest.fn(),
  delete: jest.fn(),
}));
jest.mock('../context/AuthContext', () => ({
  useAuth: jest.fn(),
}));
jest.mock('../components/Navbar', () => () => <nav data-testid="navbar" />);

import api from '../api/axios';
import { useAuth } from '../context/AuthContext';

function renderChat() {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(<Chat />);
  });
  return { container, root };
}

function setTextarea(textarea, value) {
  const setter = Object.getOwnPropertyDescriptor(
    window.HTMLTextAreaElement.prototype,
    'value'
  ).set;
  act(() => {
    setter.call(textarea, value);
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

function unmount(root) {
  act(() => root.unmount());
  document.body.innerHTML = '';
}

function mockConversaciones(conversaciones) {
  api.get.mockImplementation((url) => {
    if (url === '/chat/conversaciones') {
      return Promise.resolve({ data: conversaciones });
    }
    return Promise.resolve({ data: [] });
  });
}

function mockSesionActiva() {
  api.post.mockImplementation((url) => {
    if (url === '/chat/iniciar') {
      return Promise.resolve({ data: { sesionId: 'ses-123' } });
    }
    if (url === '/chat/ses-123/mensaje') {
      return Promise.resolve({
        data: {
          id: 2,
          origen: 'BOT',
          contenido: 'Respuesta de Aura',
          timestamp: new Date().toISOString(),
        },
      });
    }
    return Promise.reject(new Error('llamada inesperada: ' + url));
  });
}

function iniciarConversacion(container) {
  act(() => {
    container
      .querySelector('.btn-new-chat')
      .dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
}

beforeEach(() => {
  useAuth.mockReturnValue({ user: { nombre: 'Ana', rol: 'EMPLEADO' } });
  api.get.mockReset();
  api.post.mockReset();
  api.patch.mockReset();
  api.delete.mockReset();
});

describe('Chat - formato de fecha', () => {
  test('fecha de hoy se muestra como "Hoy HH:MM"', async () => {
    const hoy = new Date();
    mockConversaciones([
      { id: 1, sesionId: 'ses-1', activa: true, fechaInicio: hoy.toISOString() },
    ]);
    const { container, root } = renderChat();
    await act(async () => {});
    const dateEl = container.querySelector('.conv-item-date');
    expect(dateEl.textContent).toMatch(/^Hoy \d{1,2}:\d{2}( a\. m\.| p\. m\.)?$/);
    unmount(root);
  });

  test('fecha de ayer se muestra como "Ayer HH:MM"', async () => {
    const ayer = new Date();
    ayer.setDate(ayer.getDate() - 1);
    mockConversaciones([
      { id: 1, sesionId: 'ses-1', activa: true, fechaInicio: ayer.toISOString() },
    ]);
    const { container, root } = renderChat();
    await act(async () => {});
    const dateEl = container.querySelector('.conv-item-date');
    expect(dateEl.textContent).toMatch(/^Ayer \d{1,2}:\d{2}( a\. m\.| p\. m\.)?$/);
    unmount(root);
  });

  test('fecha antigua se muestra como fecha completa con año', async () => {
    const vieja = new Date();
    vieja.setDate(vieja.getDate() - 5);
    mockConversaciones([
      { id: 1, sesionId: 'ses-1', activa: true, fechaInicio: vieja.toISOString() },
    ]);
    const { container, root } = renderChat();
    await act(async () => {});
    const dateEl = container.querySelector('.conv-item-date');
    expect(dateEl.textContent).toMatch(/\d{4}/);
    unmount(root);
  });
});

describe('Chat - guard de input vacío', () => {
  test('con input vacío el botón de enviar está deshabilitado y no llama a la API', async () => {
    api.get.mockResolvedValue({ data: [] });
    mockSesionActiva();
    const { container, root } = renderChat();
    await act(async () => {});
    iniciarConversacion(container);
    await act(async () => {});

    const sendBtn = container.querySelector('.send-btn');
    expect(sendBtn.disabled).toBe(true);

    act(() => {
      sendBtn.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    await act(async () => {});
    expect(api.post).not.toHaveBeenCalledWith('/chat/ses-123/mensaje', expect.anything());
    unmount(root);
  });

  test('input de solo espacios no habilita el envío', async () => {
    api.get.mockResolvedValue({ data: [] });
    mockSesionActiva();
    const { container, root } = renderChat();
    await act(async () => {});
    iniciarConversacion(container);
    await act(async () => {});

    const textarea = container.querySelector('textarea.chat-input');
    setTextarea(textarea, '   ');
    const sendBtn = container.querySelector('.send-btn');
    expect(sendBtn.disabled).toBe(true);
    unmount(root);
  });
});

describe('Chat - envío de mensaje', () => {
  test('enviar agrega el mensaje del usuario y llama a la API', async () => {
    api.get.mockResolvedValue({ data: [] });
    mockSesionActiva();
    const { container, root } = renderChat();
    await act(async () => {});
    iniciarConversacion(container);
    await act(async () => {});

    const textarea = container.querySelector('textarea.chat-input');
    setTextarea(textarea, 'Hola Aura');
    const sendBtn = container.querySelector('.send-btn');
    expect(sendBtn.disabled).toBe(false);

    act(() => {
      sendBtn.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    // el mensaje del usuario aparece inmediatamente
    const userMsg = container.querySelector('.message.user .msg-bubble');
    expect(userMsg.textContent).toBe('Hola Aura');
    expect(api.post).toHaveBeenCalledWith(
      '/chat/ses-123/mensaje',
      { pregunta: 'Hola Aura' },
      { signal: expect.anything() }
    );

    // la respuesta del bot llega después
    await act(async () => {});
    const botBubbles = container.querySelectorAll('.message.bot .msg-bubble');
    const lastBot = botBubbles[botBubbles.length - 1];
    expect(lastBot.textContent).toBe('Respuesta de Aura');
    unmount(root);
  });
});

describe('Chat - respuesta del bot', () => {
  async function conversacionConRespuesta(respuesta) {
    api.get.mockResolvedValue({ data: [] });
    api.post.mockImplementation((url) => {
      if (url === '/chat/iniciar') {
        return Promise.resolve({ data: { sesionId: 'ses-123' } });
      }
      if (url === '/chat/ses-123/mensaje') {
        return Promise.resolve({ data: respuesta });
      }
      return Promise.reject(new Error('llamada inesperada: ' + url));
    });

    const { container, root } = renderChat();
    await act(async () => {});
    iniciarConversacion(container);
    await act(async () => {});

    setTextarea(container.querySelector('textarea.chat-input'), 'Hola Aura');
    act(() => {
      container
        .querySelector('.send-btn')
        .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    await act(async () => {});
    return { container, root };
  }

  test('las negritas del modelo se muestran sin asteriscos', async () => {
    const { container, root } = await conversacionConRespuesta({
      id: 2, origen: 'BOT', contenido: 'Tenes **15 días** de vacaciones',
      timestamp: new Date().toISOString(),
    });

    const burbujas = container.querySelectorAll('.message.bot .msg-bubble');
    const ultima = burbujas[burbujas.length - 1];

    expect(ultima.querySelector('strong').textContent).toBe('15 días');
    expect(ultima.textContent).not.toContain('**');
    unmount(root);
  });

  test('muestra de qué documento salió la respuesta', async () => {
    const { container, root } = await conversacionConRespuesta({
      id: 2, origen: 'BOT', contenido: 'Respuesta',
      fuentes: 'manual.pdf,politicas.pdf',
      timestamp: new Date().toISOString(),
    });

    const chips = [...container.querySelectorAll('.fuente-chip')].map((c) => c.textContent);

    expect(chips).toEqual(['manual.pdf', 'politicas.pdf']);
    unmount(root);
  });
});

describe('Chat - lista de conversaciones', () => {
  test('usa el título que manda el backend en vez del UUID', async () => {
    mockConversaciones([
      {
        id: 1, sesionId: '2a225cfd-95e7-47aa', activa: true,
        fechaInicio: new Date().toISOString(),
        titulo: '¿Cuántos días de vacaciones?',
      },
    ]);
    const { container, root } = renderChat();
    await act(async () => {});

    expect(container.querySelector('.conv-item-titulo').textContent)
      .toBe('¿Cuántos días de vacaciones?');
    expect(container.querySelector('.conv-item').textContent).not.toContain('2a225cfd');
    unmount(root);
  });
});