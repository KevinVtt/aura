import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import './Login.css';

const ESPERA_GIS_MS = 8000;
const POLL_MS = 100;

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const slotRef = useRef(null);
  const [vista, setVista] = useState('inicio');
  const [googleListo, setGoogleListo] = useState(false);
  const [perfil, setPerfil] = useState({ email: '', nombre: '' });
  const [idToken, setIdToken] = useState('');
  const [telefono, setTelefono] = useState('');
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // CRA inlinea la variable en el build; se lee por render para que el aviso
  // de configuración dependa del valor real y no de una constante del módulo.
  const clientId = process.env.REACT_APP_GOOGLE_CLIENT_ID;

  const handleCredential = useCallback(async ({ credential }) => {
    setError('');
    setLoading(true);
    try {
      const { data } = await api.post('/auth/google', { idToken: credential });
      if (data.estado === 'APROBADO') {
        login(data);
        navigate(data.rol === 'ADMIN' ? '/admin' : '/chat');
      } else if (data.estado === 'NO_REGISTRADO') {
        setPerfil({ email: data.email || '', nombre: data.nombre || '' });
        setIdToken(credential);
        setVista('registro');
      } else if (data.estado === 'PENDIENTE') {
        setVista('pendiente');
      } else if (data.estado === 'RECHAZADO') {
        setVista('rechazado');
      } else {
        setVista('inactivo');
      }
    } catch (err) {
      setError(err.response?.data?.error || 'No se pudo verificar tu cuenta de Google');
    } finally {
      setLoading(false);
    }
  }, [login, navigate]);

  // El script de Google entra con async: se reintenta hasta 8 s y, mientras
  // tanto, la pantalla muestra un aviso en vez de quedar vacía.
  useEffect(() => {
    if (!clientId) return undefined;
    if (window.google?.accounts?.id) {
      setGoogleListo(true);
      return undefined;
    }
    const inicio = Date.now();
    const id = setInterval(() => {
      if (window.google?.accounts?.id) {
        setGoogleListo(true);
        clearInterval(id);
      } else if (Date.now() - inicio > ESPERA_GIS_MS) {
        clearInterval(id);
      }
    }, POLL_MS);
    return () => clearInterval(id);
  }, [clientId]);

  useEffect(() => {
    if (!googleListo || !slotRef.current) return;
    window.google.accounts.id.initialize({ client_id: clientId, callback: handleCredential });
    window.google.accounts.id.renderButton(slotRef.current, {
      theme: 'filled_black',
      size: 'large',
      text: 'continue_with',
      shape: 'pill',
    });
  }, [googleListo, clientId, handleCredential]);

  const handle = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const solicitar = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/auth/solicitud', { idToken, telefono });
      setVista('pendiente');
    } catch (err) {
      setError(err.response?.data?.error || 'No se pudo enviar la solicitud');
    } finally {
      setLoading(false);
    }
  };

  // Único login con contraseña: el super administrador de aura.admin.email.
  const submit = async (e) => {
    e.preventDefault();
    setError('');
    const email = form.email.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError('Ingresá un correo válido, por ejemplo nombre@empresa.com');
      return;
    }
    setLoading(true);
    try {
      const { data } = await api.post('/auth/login', { ...form, email });
      login(data);
      navigate(data.rol === 'ADMIN' ? '/admin' : '/chat');
    } catch (err) {
      // El backend responde 429 sin cuerpo al superar los 5 intentos fallidos: sin
      // este caso el mensaje seria "Credenciales incorrectas", que es falso.
      setError(
        err.response?.status === 429
          ? 'Demasiados intentos fallidos. Esperá 15 minutos e intentá de nuevo.'
          : err.response?.data?.error || err.response?.data?.email || 'Credenciales incorrectas'
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
        <p className="auth-desc">
          {vista === 'admin' ? 'Acceso del super administrador' : 'Ingresá con tu cuenta de Google'}
        </p>

        {error && <div className="auth-error">{error}</div>}

        {vista === 'inicio' && (
          <>
            {!clientId && (
              <div className="auth-error">
                El login con Google no está configurado (falta REACT_APP_GOOGLE_CLIENT_ID).
              </div>
            )}
            {clientId && !googleListo && (
              <div className="auth-error">
                No se pudo cargar el botón de Google. Revisá tu conexión y recargá la página.
              </div>
            )}
            {clientId && googleListo && <div className="google-slot" ref={slotRef} />}
            {loading && <span className="spinner" />}
            <button
              type="button"
              className="link-admin"
              onClick={() => { setError(''); setVista('admin'); }}
            >
              Acceso administrador
            </button>
          </>
        )}

        {vista === 'registro' && (
          <form onSubmit={solicitar} className="auth-form">
            <p className="auth-desc">
              La cuenta {perfil.email} todavía no está registrada. Dejanos tu teléfono y el
              administrador va a revisar tu solicitud.
            </p>
            <div className="field">
              <label>Teléfono</label>
              <input
                type="tel"
                name="telefono"
                value={telefono}
                onChange={(e) => setTelefono(e.target.value)}
                placeholder="1122334455"
              />
            </div>
            <button className="btn-primary" disabled={loading}>
              {loading ? <span className="spinner" /> : 'Enviar solicitud'}
            </button>
          </form>
        )}

        {vista === 'pendiente' && (
          <div className="auth-info">
            Tu solicitud está pendiente de aprobación. Cuando el administrador la apruebe te va a
            llegar un correo y vas a poder entrar con tu cuenta de Google.
          </div>
        )}

        {vista === 'rechazado' && (
          <div className="auth-info">
            Tu acceso no fue aprobado por el administrador. Si creés que es un error, hablá con él.
          </div>
        )}

        {vista === 'inactivo' && (
          <div className="auth-info">
            Tu cuenta está desactivada. Hablá con el administrador de Aura.
          </div>
        )}

        {vista === 'admin' && (
          <form onSubmit={submit} className="auth-form">
            <div className="field">
              <label>Email</label>
              <input
                type="email"
                name="email"
                value={form.email}
                onChange={handle}
                placeholder="admin@aura.com"
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
        )}
      </div>
    </div>
  );
}
