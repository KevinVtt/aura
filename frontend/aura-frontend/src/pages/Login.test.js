import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import Login from './Login';

jest.mock('../api/axios', () => ({
  post: jest.fn(),
}));
jest.mock('../context/AuthContext', () => ({
  useAuth: jest.fn(),
}));

import api from '../api/axios';
import { useAuth } from '../context/AuthContext';

const mockLogin = jest.fn();

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

function setInput(input, value) {
  const setter = Object.getOwnPropertyDescriptor(
    window.HTMLInputElement.prototype,
    'value'
  ).set;
  act(() => {
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

function submitForm(form) {
  act(() => {
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
}

function unmount(root) {
  act(() => root.unmount());
  document.body.innerHTML = '';
}

beforeEach(() => {
  useAuth.mockReturnValue({ login: mockLogin });
  mockLogin.mockClear();
  api.post.mockReset();
});

describe('Login', () => {
  test('renderiza el formulario con email, password y botón', () => {
    const { container, root } = renderLogin();
    expect(container.querySelector('input[name="email"]')).not.toBeNull();
    expect(container.querySelector('input[name="password"]')).not.toBeNull();
    expect(container.querySelector('button.btn-primary')).not.toBeNull();
    unmount(root);
  });

  test('submit con credenciales llama a api.post y navega a /chat para EMPLEADO', async () => {
    api.post.mockResolvedValue({ data: { token: 'tok', nombre: 'Ana', rol: 'EMPLEADO' } });
    const { container, root } = renderLogin();
    setInput(container.querySelector('input[name="email"]'), 'ana@test.com');
    setInput(container.querySelector('input[name="password"]'), 'secreto');
    await act(async () => {
      container
        .querySelector('form')
        .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
    expect(api.post).toHaveBeenCalledWith('/auth/login', {
      email: 'ana@test.com',
      password: 'secreto',
    });
    expect(mockLogin).toHaveBeenCalledWith({ token: 'tok', nombre: 'Ana', rol: 'EMPLEADO' });
    expect(container.querySelector('[data-testid="chat-page"]')).not.toBeNull();
    unmount(root);
  });

  test('submit con rol ADMIN navega a /admin', async () => {
    api.post.mockResolvedValue({ data: { token: 'tok', nombre: 'Ana', rol: 'ADMIN' } });
    const { container, root } = renderLogin();
    setInput(container.querySelector('input[name="email"]'), 'admin@test.com');
    setInput(container.querySelector('input[name="password"]'), 'secreto');
    await act(async () => {
      container
        .querySelector('form')
        .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
    expect(container.querySelector('[data-testid="admin-page"]')).not.toBeNull();
    unmount(root);
  });

  test('error del servidor muestra el mensaje de error', async () => {
    api.post.mockRejectedValue({ response: { data: { error: 'Credenciales inválidas' } } });
    const { container, root } = renderLogin();
    setInput(container.querySelector('input[name="email"]'), 'ana@test.com');
    setInput(container.querySelector('input[name="password"]'), 'mal');
    await act(async () => {
      container
        .querySelector('form')
        .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
    expect(container.querySelector('.auth-error').textContent).toBe('Credenciales inválidas');
    unmount(root);
  });

  test('error sin response.data muestra mensaje por defecto', async () => {
    api.post.mockRejectedValue({});
    const { container, root } = renderLogin();
    setInput(container.querySelector('input[name="email"]'), 'ana@test.com');
    setInput(container.querySelector('input[name="password"]'), 'mal');
    await act(async () => {
      container
        .querySelector('form')
        .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
    expect(container.querySelector('.auth-error').textContent).toBe('Credenciales incorrectas');
    unmount(root);
  });

  test('429 por demasiados intentos no dice "credenciales incorrectas"', async () => {
    api.post.mockRejectedValue({ response: { status: 429 } });
    const { container, root } = renderLogin();
    setInput(container.querySelector('input[name="email"]'), 'ana@test.com');
    setInput(container.querySelector('input[name="password"]'), 'mal');
    await act(async () => {
      container
        .querySelector('form')
        .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
    expect(container.querySelector('.auth-error').textContent).toContain('Demasiados intentos');
    unmount(root);
  });

  test('loading deshabilita el botón durante el submit', async () => {
    let resolvePost;
    api.post.mockReturnValue(
      new Promise((res) => {
        resolvePost = res;
      })
    );
    const { container, root } = renderLogin();
    setInput(container.querySelector('input[name="email"]'), 'ana@test.com');
    setInput(container.querySelector('input[name="password"]'), 'secreto');
    submitForm(container.querySelector('form'));
    const button = container.querySelector('button.btn-primary');
    expect(button.disabled).toBe(true);
    expect(button.querySelector('.spinner')).not.toBeNull();
    await act(async () => {
      resolvePost({ data: { token: 'tok', nombre: 'Ana', rol: 'EMPLEADO' } });
    });
    expect(container.querySelector('[data-testid="chat-page"]')).not.toBeNull();
    unmount(root);
  });
});