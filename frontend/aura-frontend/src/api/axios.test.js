jest.mock('axios', () => {
  const handlers = { request: [], response: [] };
  const instance = {
    interceptors: {
      request: { use: (fn) => handlers.request.push(fn) },
      response: { use: (fn, errFn) => handlers.response.push({ fn, errFn }) },
    },
    defaults: {},
  };
  return {
    __esModule: true,
    default: {
      create: jest.fn(() => instance),
    },
    __handlers: handlers,
  };
});

import api from './axios';

const axiosMock = require('axios');
const handlers = axiosMock.__handlers;
const requestInterceptor = handlers.request[0];
const responseInterceptor = handlers.response[0];

beforeAll(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: { href: 'http://localhost/' },
  });
});

afterAll(() => {
  delete window.location;
});

beforeEach(() => {
  localStorage.clear();
  window.location.href = 'http://localhost/';
});

describe('axios interceptor de request', () => {
  test('agrega Authorization: Bearer <token> cuando hay token', () => {
    localStorage.setItem('token', 'jwt-abc-123');
    const config = { headers: {} };
    const result = requestInterceptor(config);
    expect(result).toBe(config);
    expect(config.headers.Authorization).toBe('Bearer jwt-abc-123');
  });

  test('no agrega Authorization cuando no hay token', () => {
    const config = { headers: {} };
    requestInterceptor(config);
    expect(config.headers.Authorization).toBeUndefined();
  });
});

describe('axios interceptor de response', () => {
  test('devuelve la respuesta tal cual en caso de éxito', () => {
    const res = { data: { ok: true }, status: 200 };
    expect(responseInterceptor.fn(res)).toBe(res);
  });

  test('en 401 limpia localStorage y redirige a /login', async () => {
    localStorage.setItem('token', 'jwt-abc-123');
    localStorage.setItem('nombre', 'Ana');
    const err = { response: { status: 401 } };
    await expect(responseInterceptor.errFn(err)).rejects.toBe(err);
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('nombre')).toBeNull();
    expect(window.location.href).toBe('/login');
  });

  test('en 401 de /auth/login NO limpia localStorage ni redirige', async () => {
    localStorage.setItem('token', 'jwt-abc-123');
    localStorage.setItem('nombre', 'Ana');
    const err = {
      response: { status: 401 },
      config: { url: '/auth/login' },
    };
    await expect(responseInterceptor.errFn(err)).rejects.toBe(err);
    expect(localStorage.getItem('token')).toBe('jwt-abc-123');
    expect(localStorage.getItem('nombre')).toBe('Ana');
    expect(window.location.href).toBe('http://localhost/');
  });

  test('en error distinto de 401 NO limpia localStorage ni redirige', async () => {
    localStorage.setItem('token', 'jwt-abc-123');
    const err = { response: { status: 500 } };
    await expect(responseInterceptor.errFn(err)).rejects.toBe(err);
    expect(localStorage.getItem('token')).toBe('jwt-abc-123');
    expect(window.location.href).toBe('http://localhost/');
  });

  test('en error de red (sin response) NO limpia localStorage ni redirige', async () => {
    localStorage.setItem('token', 'jwt-abc-123');
    const err = new Error('Network Error');
    await expect(responseInterceptor.errFn(err)).rejects.toBe(err);
    expect(localStorage.getItem('token')).toBe('jwt-abc-123');
    expect(window.location.href).toBe('http://localhost/');
  });
});