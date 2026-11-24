import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import Login from './Login';

jest.mock('../api/axios', () => ({ post: jest.fn() }));
jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));

import api from '../api/axios';
import { useAuth } from '../context/AuthContext';

const mockLogin = jest.fn();
let googleCallback;

function renderLogin() {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/admin" element={<div data-testid="admin-page" />} />
          <Route path="/chat" element={<div data-testid="chat-page" />} />
        </Routes>
      </MemoryRouter>
    );
  });
  return { container, root };
}

async function credencial(credential) {
  await act(async () => { await googleCallback({ credential }); });
}

beforeEach(() => {
  process.env.REACT_APP_GOOGLE_CLIENT_ID = 'client-id-de-prueba';
  useAuth.mockReturnValue({ login: mockLogin });
  mockLogin.mockClear();
  api.post.mockReset();
  window.google = {
    accounts: {
      id: {
        initialize: jest.fn(({ callback }) => { googleCallback = callback; }),
        renderButton: jest.fn(),
      },
    },
  };
});

afterEach(() => {
  delete window.google;
  delete process.env.REACT_APP_GOOGLE_CLIENT_ID;
  document.body.innerHTML = '';
});

test('sin REACT_APP_GOOGLE_CLIENT_ID muestra un aviso de configuración', () => {
  delete process.env.REACT_APP_GOOGLE_CLIENT_ID;
  const { container, root } = renderLogin();
  expect(container.textContent).toContain('no está configurado');
  act(() => root.unmount());
});

test('si el script de Google no carga, muestra un aviso en vez de quedar vacío', () => {
  delete window.google;
  const { container, root } = renderLogin();
  expect(container.querySelector('.auth-error')).not.toBeNull();
  act(() => root.unmount());
});

test('credencial APROBADA inicia sesión y navega según el rol', async () => {
  const data = { estado: 'APROBADO', token: 'tok', nombre: 'Ana', rol: 'EMPLEADO' };
  api.post.mockResolvedValue({ data });
  const { container, root } = renderLogin();
  await credencial('id-token');
  expect(api.post).toHaveBeenCalledWith('/auth/google', { idToken: 'id-token' });
  expect(mockLogin).toHaveBeenCalledWith(data);
  expect(container.querySelector('[data-testid="chat-page"]')).not.toBeNull();
  act(() => root.unmount());
});

test('estado PENDIENTE muestra el mensaje y no inicia sesión', async () => {
  api.post.mockResolvedValue({ data: { estado: 'PENDIENTE', email: 'ana@gmail.com' } });
  const { container, root } = renderLogin();
  await credencial('id-token');
  expect(container.textContent).toContain('pendiente de aprobación');
  expect(mockLogin).not.toHaveBeenCalled();
  act(() => root.unmount());
});

test('estado NO_REGISTRADO pide el teléfono y manda la solicitud', async () => {
  api.post.mockResolvedValueOnce({ data: { estado: 'NO_REGISTRADO', email: 'ana@gmail.com', nombre: 'Ana' } });
  const { container, root } = renderLogin();
  await credencial('id-token');
  const tel = container.querySelector('input[name="telefono"]');
  expect(tel).not.toBeNull();
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  act(() => { setter.call(tel, '1122334455'); tel.dispatchEvent(new Event('input', { bubbles: true })); });
  api.post.mockResolvedValueOnce({ data: {} });
  await act(async () => {
    container.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  expect(api.post).toHaveBeenLastCalledWith('/auth/solicitud', { idToken: 'id-token', telefono: '1122334455' });
  expect(container.textContent).toContain('pendiente de aprobación');
  act(() => root.unmount());
});

test('estado RECHAZADO muestra el mensaje de rechazo', async () => {
  api.post.mockResolvedValue({ data: { estado: 'RECHAZADO' } });
  const { container, root } = renderLogin();
  await credencial('id-token');
  expect(container.textContent).toContain('no fue aprobado');
  act(() => root.unmount());
});

test('el acceso de administrador manda a /auth/login', async () => {
  api.post.mockResolvedValue({ data: { token: 'tok', nombre: 'Admin', rol: 'ADMIN' } });
  const { container, root } = renderLogin();
  act(() => {
    container.querySelector('.link-admin').dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  const email = container.querySelector('input[name="email"]');
  const pass = container.querySelector('input[name="password"]');
  act(() => {
    setter.call(email, 'admin@aura.com'); email.dispatchEvent(new Event('input', { bubbles: true }));
    setter.call(pass, 'secreto'); pass.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await act(async () => {
    container.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  expect(api.post).toHaveBeenCalledWith('/auth/login', { email: 'admin@aura.com', password: 'secreto' });
  expect(container.querySelector('[data-testid="admin-page"]')).not.toBeNull();
  act(() => root.unmount());
});
