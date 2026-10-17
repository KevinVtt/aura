import { useState, useEffect, useRef } from 'react';
import Navbar from '../components/Navbar';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import './Chat.css';

export default function Chat() {
  const { user } = useAuth();
  const [sesionId, setSesionId] = useState(null);
  const [mensajes, setMensajes] = useState([]);
  const [conversaciones, setConversaciones] = useState([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [iniciando, setIniciando] = useState(false);
  const bottomRef = useRef(null);
  const inputRef = useRef(null);
  const inactivityRef = useRef(null);

  useEffect(() => {
    return () => clearTimeout(inactivityRef.current);
  }, []);

  useEffect(() => {
    cargarConversaciones();
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [mensajes]);

  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.style.height = 'auto';
      inputRef.current.style.height = Math.min(inputRef.current.scrollHeight, 120) + 'px';
    }
  }, [input]);

  const resetInactivity = () => {
    clearTimeout(inactivityRef.current);
    inactivityRef.current = setTimeout(async () => {
      if (sesionId) {
        await api.patch(`/chat/${sesionId}/cerrar`);
        cargarConversaciones();
      }
    }, 15 * 60 * 1000);
  };

  const cargarConversaciones = async () => {
    try {
      const { data } = await api.get('/chat/conversaciones');
      setConversaciones(data);
    } catch {}
  };

  const iniciarConversacion = async () => {
    setIniciando(true);
    setMensajes([]);
    try {
      const { data } = await api.post('/chat/iniciar');
      setSesionId(data.sesionId);
      cargarConversaciones();
    } catch {
      setMensajes([{
        id: 0, origen: 'BOT',
        contenido: 'Error al conectar. Verificá que Spring Boot esté corriendo.',
        timestamp: new Date().toISOString()
      }]);
    } finally {
      setIniciando(false);
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  };

  const abrirConversacion = async (conv) => {
    setSesionId(conv.sesionId);
    try {
      const { data } = await api.get(`/chat/${conv.sesionId}/mensajes`);
      setMensajes(data);
    } catch {
      setMensajes([]);
    }
    setTimeout(() => inputRef.current?.focus(), 100);
  };

  const eliminarConversacion = async (e, sesId) => {
    e.stopPropagation();
    if (!window.confirm('¿Eliminar esta conversación?')) return;
    try {
      await api.delete(`/chat/${sesId}`);
      if (sesionId === sesId) {
        setSesionId(null);
        setMensajes([]);
      }
      cargarConversaciones();
    } catch {}
  };

  const enviar = async () => {
    if (!input.trim() || loading || !sesionId) return;
    const pregunta = input.trim();
    setInput('');
    resetInactivity();
    const msgUser = {
      id: Date.now(), origen: 'EMPLEADO',
      usuario: { nombre: user?.nombre },
      contenido: pregunta,
      timestamp: new Date().toISOString(),
    };
    setMensajes((prev) => [...prev, msgUser]);
    setLoading(true);

    try {
      const { data } = await api.post(
        `/chat/${sesionId}/mensaje`,
        { pregunta }
      );
      setMensajes((prev) => [...prev, data]);
    } catch (err) {
      const detalle =
        err.response?.data?.detail ||
        err.response?.data?.message ||
        'No se pudo contactar al servicio de IA.';
      setMensajes((prev) => [...prev, {
        id: Date.now() + 1, origen: 'BOT',
        contenido: String(detalle).slice(0, 200),
        timestamp: new Date().toISOString(),
      }]);
    } finally {
      setLoading(false);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  };

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      enviar();
    }
  };

  const suggestions = [
    '¿Qué información hay disponible en los documentos?',
    'Resumí las políticas de la empresa',
    '¿Cuáles son los procedimientos de seguridad?',
  ];

  const formatDate = (ts) => {
    const d = new Date(ts);
    const now = new Date();
    const diffDays = Math.floor((now.setHours(0,0,0,0) - new Date(d).setHours(0,0,0,0)) / 86400000);
    if (diffDays === 0) return 'Hoy ' + d.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' });
    if (diffDays === 1) return 'Ayer ' + d.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' });
    return d.toLocaleDateString('es-AR', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  };

  const formatTime = (ts) => new Date(ts).toLocaleTimeString('es-AR', {
    hour: '2-digit', minute: '2-digit'
  });

  return (
    <div className="chat-layout">
      <Navbar />
      <div className="chat-container">

        {/* Sidebar */}
        <aside className="chat-sidebar">
          <div className="sidebar-header">
            <span className="sidebar-title-icon">A</span>
            <span>Conversaciones</span>
          </div>

          <button className="btn-new-chat" onClick={iniciarConversacion} disabled={iniciando}>
            {iniciando ? <span className="spinner" style={{width:14,height:14}}/> : (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            )}
            Nueva conversación
          </button>

          <div className="conv-list">
            {conversaciones.length === 0 ? (
              <div className="conv-empty">Sin conversaciones</div>
            ) : (
              conversaciones.map((conv) => (
                <div
                  key={conv.id}
                  className={`conv-item ${sesionId === conv.sesionId ? 'active' : ''}`}
                  onClick={() => abrirConversacion(conv)}
                >
                  <div className="conv-item-header">
                    <span className={`conv-item-indicator ${conv.activa ? 'open' : 'closed'}`} />
                    <span className="conv-item-date">{formatDate(conv.fechaInicio)}</span>
                    <button
                      className="conv-delete"
                      onClick={(e) => eliminarConversacion(e, conv.sesionId)}
                      title="Eliminar"
                    >
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="3,6 5,6 21,6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
                    </button>
                  </div>
                  <span className="conv-item-preview mono">{conv.sesionId.substring(0, 16)}...</span>
                </div>
              ))
            )}
          </div>

          <div className="sidebar-tips">
            <p className="tips-title">Consejos</p>
            <ul>
              <li>Hacé preguntas específicas sobre los documentos</li>
              <li>Podés retomar conversaciones anteriores</li>
              <li>Si no encuentra info, probá reformulando</li>
            </ul>
          </div>
        </aside>

        {/* Chat principal */}
        <main className="chat-main">
          <div className="messages-area">
            {!sesionId ? (
              <div className="chat-welcome fade-in">
                <div className="welcome-icon">A</div>
                <h2>¿En qué puedo ayudarte?</h2>
                <p>Iniciá una nueva conversación o seleccioná una anterior</p>
                <button className="btn-primary" onClick={iniciarConversacion}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                  Nueva conversación
                </button>
                <div className="suggestions">
                  {suggestions.map((s) => (
                    <button key={s} className="suggestion"
                      onClick={() => {
                        iniciarConversacion();
                        setTimeout(() => setInput(s), 300);
                      }}>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            ) : iniciando ? (
              <div className="chat-init">
                <div className="init-spinner"><span className="spinner" style={{width:28,height:28}}/></div>
                <span>Iniciando conversación...</span>
              </div>
            ) : mensajes.length === 0 ? (
              <div className="chat-welcome fade-in">
                <div className="welcome-icon">A</div>
                <h2>Conversación iniciada</h2>
                <p>Escribí tu primera pregunta o elegí una sugerencia</p>
                <div className="suggestions">
                  {suggestions.map((s) => (
                    <button key={s} className="suggestion"
                      onClick={() => {
                        setInput(s);
                        inputRef.current?.focus();
                      }}>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              mensajes.map((msg, i) => (
                <div
                  key={msg.id || i}
                  className={`message ${msg.origen === 'EMPLEADO' ? 'user' : 'bot'} fade-in`}
                  style={{ animationDelay: `${i * 0.03}s` }}
                >
                  <div className="msg-avatar">
                    {msg.origen === 'EMPLEADO' ? (user?.nombre?.charAt(0) || 'U') : 'A'}
                  </div>
                  <div className="msg-content">
                    <div className="msg-header">
                      <span className="msg-author">
                        {msg.origen === 'EMPLEADO' ? (msg.usuario?.nombre || user?.nombre) : 'Aura'}
                      </span>
                      <span className="msg-time">{formatTime(msg.timestamp)}</span>
                    </div>
                    <div className="msg-bubble">{msg.contenido}</div>
                  </div>
                </div>
              ))
            )}

            {loading && (
              <div className="message bot fade-in">
                <div className="msg-avatar">A</div>
                <div className="msg-content">
                  <div className="msg-header"><span className="msg-author">Aura</span></div>
                  <div className="msg-bubble typing">
                    <span /><span /><span />
                  </div>
                </div>
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          {/* Input */}
          <div className="chat-input-area">
            <div className="input-wrapper">
              <textarea
                ref={inputRef}
                className="chat-input"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKey}
                placeholder={sesionId ? "Escribí tu pregunta..." : "Iniciá una conversación primero"}
                rows={1}
                disabled={loading || !sesionId}
              />
              <button
                className="send-btn"
                onClick={enviar}
                disabled={loading || !input.trim() || !sesionId}
              >
                {loading ? <span className="spinner" /> : (
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22,2 15,22 11,13 2,9"/></svg>
                )}
              </button>
            </div>
            <p className="input-hint">Enter para enviar · Shift+Enter para nueva línea</p>
          </div>
        </main>
      </div>
    </div>
  );
}
