import { createContext, useContext, useState, useCallback } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const token = localStorage.getItem('token');
    const nombre = localStorage.getItem('nombre');
    const rol = localStorage.getItem('rol');
    const mustChangePassword = localStorage.getItem('mustChangePassword') === 'true';
    return token ? { token, nombre, rol, mustChangePassword } : null;
  });

  const login = useCallback((data) => {
    localStorage.setItem('token', data.token);
    localStorage.setItem('nombre', data.nombre);
    localStorage.setItem('rol', data.rol);
    localStorage.setItem('mustChangePassword', String(!!data.mustChangePassword));
    setUser({ token: data.token, nombre: data.nombre, rol: data.rol, mustChangePassword: !!data.mustChangePassword });
  }, []);

  const logout = useCallback(() => {
    localStorage.clear();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
