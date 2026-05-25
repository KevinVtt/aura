import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import './Login.css';

export default function Register() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    nombre: '',
    password: '',
    rol: 'EMPLEADO',
    contacto: { email: '', telefono: '' },
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handle = (e) => {
    const { name, value } = e.target;
    if (name === 'email' || name === 'telefono') {
      setForm({ ...form, contacto: { ...form.contacto, [name]: value } });
    } else {
      setForm({ ...form, [name]: value });
    }
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (form.password.length < 8) {
      setError('La contraseña debe tener al menos 8 caracteres');
      return;
    }
    setLoading(true);
    try {
      const { data } = await api.post('/auth/register', form);
      login(data);
      navigate(data.rol === 'ADMIN' ? '/admin' : '/chat');
    } catch (err) {
      setError(err.response?.data?.error || 'Error al registrarse');
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
        <h2 className="auth-title">Crear cuenta</h2>
        <p className="auth-desc">Completá tus datos para registrarte</p>

        {error && <div className="auth-error">{error}</div>}

        <form onSubmit={submit} className="auth-form">
          <div className="field">
            <label>Nombre completo</label>
            <input
              name="nombre"
              value={form.nombre}
              onChange={handle}
              placeholder="Juan Pérez"
              required
            />
          </div>
          <div className="field">
            <label>Email</label>
            <input
              type="email"
              name="email"
              value={form.contacto.email}
              onChange={handle}
              placeholder="tu@email.com"
              required
            />
          </div>
          <div className="field">
            <label>Teléfono</label>
            <input
              name="telefono"
              value={form.contacto.telefono}
              onChange={handle}
              placeholder="1111111111"
            />
          </div>
          <div className="field">
            <label>Contraseña</label>
            <input
              type="password"
              name="password"
              value={form.password}
              onChange={handle}
              placeholder="Mínimo 8 caracteres"
              required
            />
          </div>
          <div className="field">
            <label>Rol</label>
            <select name="rol" value={form.rol} onChange={handle}>
              <option value="EMPLEADO">Empleado</option>
              <option value="ADMIN">Administrador</option>
            </select>
          </div>
          <button className="btn-primary" disabled={loading}>
            {loading ? <span className="spinner" /> : 'Crear cuenta'}
          </button>
        </form>

        <p className="auth-footer">
          ¿Ya tenés cuenta?{' '}
          <Link to="/login">Iniciar sesión</Link>
        </p>
      </div>
    </div>
  );
}
