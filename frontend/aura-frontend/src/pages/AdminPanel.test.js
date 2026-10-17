import { act } from 'react';
import { createRoot } from 'react-dom/client';
import AdminPanel from './AdminPanel';

jest.mock('../api/axios', () => ({
  get: jest.fn(),
  post: jest.fn(),
  patch: jest.fn(),
  delete: jest.fn(),
}));
jest.mock('../components/Navbar', () => () => <nav data-testid="navbar" />);

import api from '../api/axios';

function renderPanel() {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(<AdminPanel />);
  });
  return { container, root };
}

function unmount(root) {
  act(() => root.unmount());
  document.body.innerHTML = '';
}

// El panel abre en la pestaña Documentos: hay que ir a Usuarios.
async function irAUsuarios(container) {
  act(() => {
    container.querySelectorAll('.tab')[1].dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  await act(async () => {});
}

function empleado(extra = {}) {
  return {
    id: 2, nombre: 'Juan', rol: 'EMPLEADO', activo: true,
    contacto: { email: 'juan@aura.com' }, esSuperAdmin: false, ...extra,
  };
}

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
  api.patch.mockReset();
  api.delete.mockReset();
  api.get.mockImplementation((url) => {
    if (url === '/usuarios') return Promise.resolve({ data: [empleado()] });
    return Promise.resolve({ data: [] });
  });
});

describe('AdminPanel - cambio de rol', () => {
  test('el rol de un usuario se elige de una lista', async () => {
    const { container, root } = renderPanel();
    await act(async () => {});
    await irAUsuarios(container);

    const select = container.querySelector('.role-select');
    expect(select).not.toBeNull();
    expect([...select.options].map((o) => o.value)).toEqual(['EMPLEADO', 'ADMIN']);
    expect(select.value).toBe('EMPLEADO');
    unmount(root);
  });

  test('elegir otro rol lo manda al backend', async () => {
    api.patch.mockResolvedValue({ data: {} });
    const { container, root } = renderPanel();
    await act(async () => {});
    await irAUsuarios(container);

    const select = container.querySelector('.role-select');
    const setter = Object.getOwnPropertyDescriptor(
      window.HTMLSelectElement.prototype, 'value'
    ).set;
    act(() => {
      setter.call(select, 'ADMIN');
      select.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await act(async () => {});

    expect(api.patch).toHaveBeenCalledWith('/usuarios/2/rol', { rol: 'ADMIN' });
    unmount(root);
  });

  test('al super administrador no se le ofrece cambiar el rol ni desactivarlo', async () => {
    api.get.mockImplementation((url) => {
      if (url === '/usuarios') {
        return Promise.resolve({
          data: [empleado({ id: 1, nombre: 'Admin', rol: 'ADMIN', esSuperAdmin: true })],
        });
      }
      return Promise.resolve({ data: [] });
    });

    const { container, root } = renderPanel();
    await act(async () => {});
    await irAUsuarios(container);

    const fila = container.querySelector('.user-item');
    expect(fila.querySelector('.role-select')).toBeNull();
    expect(fila.querySelector('.role-badge').textContent).toBe('ADMIN');
    expect(fila.querySelector('.btn-danger')).toBeNull();
    unmount(root);
  });
});
