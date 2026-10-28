import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import './Login.css';

export default function CambiarPassword() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ passwordActual: '', passwordNueva: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (!user) return <Navigate to="/login" replace />;

  const handle = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/auth/cambiar-password', form);
      login({ ...user, mustChangePassword: false });
      navigate(user.rol === 'ADMIN' ? '/admin' : '/chat');
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.detail || 'No se pudo cambiar la contraseña');
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
        <h2 className="auth-title">Cambiá tu contraseña</h2>
        <p className="auth-desc">
          {user.mustChangePassword
            ? 'Es tu primer ingreso. Elegí una contraseña nueva para continuar.'
            : 'Ingresá tu contraseña actual y elegí una nueva.'}
        </p>

        {error && <div className="auth-error">{error}</div>}

        <form onSubmit={submit} className="auth-form">
          <div className="field">
            <label>Contraseña actual</label>
            <input type="password" name="passwordActual" value={form.passwordActual} onChange={handle} required />
          </div>
          <div className="field">
            <label>Contraseña nueva</label>
            <input type="password" name="passwordNueva" value={form.passwordNueva} onChange={handle} placeholder="Mínimo 8 caracteres" minLength={8} required />
          </div>
          <button className="btn-primary" disabled={loading}>
            {loading ? <span className="spinner" /> : 'Guardar contraseña'}
          </button>
        </form>
      </div>
    </div>
  );
}
