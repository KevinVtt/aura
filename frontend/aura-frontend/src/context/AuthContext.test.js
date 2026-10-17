import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { AuthProvider, useAuth } from './AuthContext';

function Consumer() {
  const { user, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="token">{user ? user.token : 'null'}</span>
      <span data-testid="nombre">{user ? user.nombre : 'null'}</span>
      <span data-testid="rol">{user ? user.rol : 'null'}</span>
      <button
        id="btn-login"
        onClick={() => login({ token: 'tok-1', nombre: 'Ana', rol: 'ADMIN' })}
      >
        login
      </button>
      <button id="btn-logout" onClick={logout}>
        logout
      </button>
    </div>
  );
}

function renderProvider() {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    );
  });
  return { container, root };
}

function unmount(root) {
  act(() => root.unmount());
  document.body.innerHTML = '';
}

beforeEach(() => localStorage.clear());
afterEach(() => localStorage.clear());

describe('AuthContext', () => {
  test('estado inicial es null cuando localStorage está vacío', () => {
    const { container, root } = renderProvider();
    expect(container.querySelector('[data-testid="token"]').textContent).toBe('null');
    expect(container.querySelector('[data-testid="nombre"]').textContent).toBe('null');
    expect(container.querySelector('[data-testid="rol"]').textContent).toBe('null');
    unmount(root);
  });

  test('estado inicial se restaura desde localStorage', () => {
    localStorage.setItem('token', 'tok-persistido');
    localStorage.setItem('nombre', 'Carlos');
    localStorage.setItem('rol', 'EMPLEADO');
    const { container, root } = renderProvider();
    expect(container.querySelector('[data-testid="token"]').textContent).toBe('tok-persistido');
    expect(container.querySelector('[data-testid="nombre"]').textContent).toBe('Carlos');
    expect(container.querySelector('[data-testid="rol"]').textContent).toBe('EMPLEADO');
    unmount(root);
  });

  test('login guarda token/nombre/rol en localStorage y actualiza el estado', () => {
    const { container, root } = renderProvider();
    act(() => {
      container
        .querySelector('#btn-login')
        .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    expect(localStorage.getItem('token')).toBe('tok-1');
    expect(localStorage.getItem('nombre')).toBe('Ana');
    expect(localStorage.getItem('rol')).toBe('ADMIN');
    expect(container.querySelector('[data-testid="token"]').textContent).toBe('tok-1');
    expect(container.querySelector('[data-testid="nombre"]').textContent).toBe('Ana');
    expect(container.querySelector('[data-testid="rol"]').textContent).toBe('ADMIN');
    unmount(root);
  });

  test('logout limpia localStorage y pone el usuario en null', () => {
    localStorage.setItem('token', 'tok-1');
    localStorage.setItem('nombre', 'Ana');
    localStorage.setItem('rol', 'ADMIN');
    localStorage.setItem('otra-key', 'valor-no-auth');
    const { container, root } = renderProvider();
    act(() => {
      container
        .querySelector('#btn-logout')
        .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('nombre')).toBeNull();
    expect(localStorage.getItem('rol')).toBeNull();
    expect(container.querySelector('[data-testid="token"]').textContent).toBe('null');
    unmount(root);
  });
});