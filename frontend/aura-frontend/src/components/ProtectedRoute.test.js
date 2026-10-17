import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';

jest.mock('../context/AuthContext', () => ({
  useAuth: jest.fn(),
}));

import { useAuth } from '../context/AuthContext';

function LocationDisplay() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
}

function renderProtected(children, { user } = {}, roles) {
  useAuth.mockReturnValue({ user });
  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route
            path="/admin"
            element={<ProtectedRoute roles={roles}>{children}</ProtectedRoute>}
          />
          <Route path="/login" element={<LocationDisplay />} />
          <Route path="/chat" element={<LocationDisplay />} />
        </Routes>
      </MemoryRouter>
    );
  });
  return { container, root };
}

function unmount(root) {
  act(() => root.unmount());
  document.body.innerHTML = '';
}

describe('ProtectedRoute', () => {
  test('sin usuario redirige a /login', () => {
    const { container, root } = renderProtected(
      <div data-testid="protected">Contenido protegido</div>,
      { user: null },
      ['ADMIN']
    );
    expect(container.querySelector('[data-testid="location"]').textContent).toBe('/login');
    expect(container.querySelector('[data-testid="protected"]')).toBeNull();
    unmount(root);
  });

  test('usuario sin el rol requerido redirige a /chat', () => {
    const { container, root } = renderProtected(
      <div data-testid="protected">Contenido protegido</div>,
      { user: { token: 't', nombre: 'Ana', rol: 'EMPLEADO' } },
      ['ADMIN']
    );
    expect(container.querySelector('[data-testid="location"]').textContent).toBe('/chat');
    expect(container.querySelector('[data-testid="protected"]')).toBeNull();
    unmount(root);
  });

  test('usuario con el rol requerido renderiza children', () => {
    const { container, root } = renderProtected(
      <div data-testid="protected">Contenido protegido</div>,
      { user: { token: 't', nombre: 'Ana', rol: 'ADMIN' } },
      ['ADMIN']
    );
    expect(container.querySelector('[data-testid="protected"]').textContent).toBe(
      'Contenido protegido'
    );
    unmount(root);
  });

  test('EMPLEADO puede acceder cuando roles incluye EMPLEADO', () => {
    const { container, root } = renderProtected(
      <div data-testid="protected">Contenido protegido</div>,
      { user: { token: 't', nombre: 'Ana', rol: 'EMPLEADO' } },
      ['ADMIN', 'EMPLEADO']
    );
    expect(container.querySelector('[data-testid="protected"]').textContent).toBe(
      'Contenido protegido'
    );
    unmount(root);
  });

  test('sin prop roles no restringe por rol', () => {
    const { container, root } = renderProtected(
      <div data-testid="protected">Contenido protegido</div>,
      { user: { token: 't', nombre: 'Ana', rol: 'EMPLEADO' } }
    );
    expect(container.querySelector('[data-testid="protected"]').textContent).toBe(
      'Contenido protegido'
    );
    unmount(root);
  });
});