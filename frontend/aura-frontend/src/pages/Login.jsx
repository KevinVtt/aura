import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import './Login.css';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handle = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const { data } = await api.post('/auth/login', form);
      login(data);
      navigate(data.rol === 'ADMIN' ? '/admin' : '/chat');
    } catch (err) {
      // El backend responde 429 sin cuerpo al superar los 5 intentos fallidos: sin
      // este caso el mensaje seria "Credenciales incorrectas", que es falso.
      setError(
        err.response?.status === 429
          ? 'Demasiados intentos fallidos. Esperá 15 minutos e intentá de nuevo.'
          : err.response?.data?.error || 'Credenciales incorrectas'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-layout">
      <div className="auth-brand fade-in">
        <div className="brand-mark">A</div>
        <h1 className="brand-name">Aura</h1>
        <p className="brand-sub">Asistente IA para empresas</p>
      </div>

      <div className="auth-card fade-in" style={{ animationDelay: '.1s' }}>
        <h2 className="auth-title">Iniciar sesión</h2>
        <p className="auth-desc">Ingresá con tu cuenta para continuar</p>

        {error && <div className="auth-error">{error}</div>}

        <form onSubmit={submit} className="auth-form">
          <div className="field">
            <label>Email</label>
            <input
              type="email"
              name="email"
              value={form.email}
              onChange={handle}
              placeholder="tu@email.com"
              required
            />
          </div>
          <div className="field">
            <label>Contraseña</label>
            <input
              type="password"
              name="password"
              value={form.password}
              onChange={handle}
              placeholder="••••••••"
              required
            />
          </div>
          <button className="btn-primary" disabled={loading}>
            {loading ? <span className="spinner" /> : 'Ingresar'}
          </button>
        </form>

      </div>
    </div>
  );
}
