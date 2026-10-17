import { useState, useEffect, useRef } from 'react';
import Navbar from '../components/Navbar';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import './Chat.css';

// El modelo contesta con markdown (**negrita**, listas). Se arma con nodos de
// React y no con HTML: el texto viene del modelo y no se debe inyectar crudo.
function conNegritas(texto) {
  return String(texto)
    .split(/(\*\*[^*\n]+\*\*)/g)
    .map((parte, i) =>
      parte.startsWith('**') && parte.endsWith('**') && parte.length > 4 ? (
        <strong key={i}>{parte.slice(2, -2)}</strong>
      ) : (
        parte
      )
    );
}

function Respuesta({ texto }) {
  const bloques = [];
  let vinetas = [];

  const cerrarLista = () => {
    if (vinetas.length) {
      bloques.push(<ul key={`ul-${bloques.length}`}>{vinetas}</ul>);
      vinetas = [];
    }
  };

  String(texto).split('\n').forEach((linea, i) => {
    const vineta = linea.match(/^\s*[-*•]\s+(.*)$/);
    if (vineta) {
      vinetas.push(<li key={i}>{conNegritas(vineta[1])}</li>);
      return;
    }
    cerrarLista();
    if (linea.trim()) bloques.push(<p key={i}>{conNegritas(linea)}</p>);
  });
  cerrarLista();

  return bloques;
}

export default function Chat() {
  const { user } = useAuth();
  const [sesionId, setSesionId] = useState(null);
  const [mensajes, setMensajes] = useState([]);
  const [conversaciones, setConversaciones] = useState([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [iniciando, setIniciando] = useState(false);
  const [esperando, setEsperando] = useState(0);
  const [busqueda, setBusqueda] = useState('');
  const [copiado, setCopiado] = useState(null);
  const bottomRef = useRef(null);
  const inputRef = useRef(null);
  const inactivityRef = useRef(null);
  const abortRef = useRef(null);

  useEffect(() => {
    return () => {
      clearTimeout(inactivityRef.current);
      abortRef.current?.abort();
    };
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

  // Sin esto el usuario mira tres puntos sin saber si el sistema sigue vivo:
  // Groq tiene picos de 10 a 20 segundos.
  useEffect(() => {
    if (!loading) {
      setEsperando(0);
      return undefined;
    }
    const inicio = Date.now();
    const id = setInterval(
      () => setEsperando(Math.floor((Date.now() - inicio) / 1000)),
      1000
    );
    return () => clearInterval(id);
  }, [loading]);

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
      return data.sesionId;
    } catch {
      setMensajes([{
        id: 0, origen: 'BOT',
        contenido: 'Error al conectar. Verificá que Spring Boot esté corriendo.',
        timestamp: new Date().toISOString()
      }]);
      return null;
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

  // Borra TODO el historial del usuario logueado (no el de los demás).
  const vaciarHistorial = async () => {
    if (!window.confirm('¿Borrar todas tus conversaciones? No se puede deshacer.')) return;
    try {
      await api.delete('/chat/conversaciones');
      setSesionId(null);
      setMensajes([]);
      setBusqueda('');
      cargarConversaciones();
    } catch {}
  };

  const enviar = async (texto, sesion = sesionId) => {
    const pregunta = String(texto ?? input).trim();
    if (!pregunta || loading || !sesion) return;
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

    const control = new AbortController();
    abortRef.current = control;

    try {
      const { data } = await api.post(
        `/chat/${sesion}/mensaje`,
        { pregunta },
        { signal: control.signal }
      );
      setMensajes((prev) => [...prev, data]);
    } catch (err) {
      const detalle = control.signal.aborted
        ? 'Cancelaste la consulta.'
        : err.response?.data?.detail ||
          err.response?.data?.message ||
          'No se pudo contactar al servicio de IA.';
      setMensajes((prev) => [...prev, {
        id: Date.now() + 1, origen: 'BOT',
        contenido: String(detalle).slice(0, 200),
        timestamp: new Date().toISOString(),
      }]);
    } finally {
      abortRef.current = null;
      setLoading(false);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  };

  const usarSugerencia = async (texto) => {
    if (sesionId) {
      enviar(texto);
      return;
    }
    const nueva = await iniciarConversacion();
    if (nueva) enviar(texto, nueva);
  };

  const copiar = async (texto) => {
    try {
      await navigator.clipboard.writeText(texto);
      setCopiado(texto);
      setTimeout(() => setCopiado(null), 1500);
    } catch {}
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

  // Agrupado por dia: el historial se lee de un vistazo sin abrir cada item.
  const diaDe = (ts) => {
    const d = new Date(ts);
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  };

  const etiquetaDia = (ts) => {
    const dias = Math.round((diaDe(Date.now()) - diaDe(ts)) / 86400000);
    if (dias === 0) return 'Hoy';
    if (dias === 1) return 'Ayer';
    return new Date(ts).toLocaleDateString('es-AR', {
      day: '2-digit', month: '2-digit', year: 'numeric',
    });
  };

  const grupos = [];
  conversaciones
    .filter((c) => (c.titulo || '').toLowerCase().includes(busqueda.trim().toLowerCase()))
    .sort((a, b) => new Date(b.fechaInicio) - new Date(a.fechaInicio))
    .forEach((conv) => {
      const dia = diaDe(conv.fechaInicio);
      const ultimo = grupos[grupos.length - 1];
      if (ultimo && ultimo.dia === dia) ultimo.items.push(conv);
      else grupos.push({ dia, etiqueta: etiquetaDia(conv.fechaInicio), items: [conv] });
    });

  const formatTime = (ts) => new Date(ts).toLocaleTimeString('es-AR', {
    hour: '2-digit', minute: '2-digit'
  });

  return (
    <div className="chat-layout">
      <Navbar />
      <div className="chat-container">

        {/* Sidebar */}
        <aside className="chat-sidebar">
          <button className="btn-new-chat" onClick={iniciarConversacion} disabled={iniciando}>
            {iniciando ? <span className="spinner" style={{width:14,height:14}}/> : (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            )}
            Nueva conversación
          </button>

          <input
            className="conv-search"
            type="search"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar en el historial..."
            aria-label="Buscar en el historial"
          />

          <div className="conv-list">
            {grupos.length === 0 ? (
              <div className="conv-empty">
                {busqueda.trim() ? 'Sin resultados' : 'Sin conversaciones'}
              </div>
            ) : (
              grupos.map((grupo) => (
                <div className="conv-group" key={grupo.dia}>
                  <span className="conv-group-label">{grupo.etiqueta}</span>
                  {grupo.items.map((conv) => (
                    <div
                      key={conv.id}
                      className={`conv-item ${sesionId === conv.sesionId ? 'active' : ''}`}
                      onClick={() => abrirConversacion(conv)}
                    >
                      <span className="conv-item-titulo">
                        {conv.titulo || 'Sin mensajes todavía'}
                      </span>
                      <span className="conv-item-header">
                        <span className={`conv-item-indicator ${conv.activa ? 'open' : 'closed'}`} />
                        <span className="conv-item-date">{formatTime(conv.fechaInicio)}</span>
                        <button
                          className="conv-delete"
                          onClick={(e) => eliminarConversacion(e, conv.sesionId)}
                          title="Eliminar"
                        >
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="3,6 5,6 21,6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
                        </button>
                      </span>
                    </div>
                  ))}
                </div>
              ))
            )}
          </div>

          {conversaciones.length > 0 && (
            <button className="btn-vaciar" onClick={vaciarHistorial}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="3,6 5,6 21,6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
              Vaciar historial
            </button>
          )}
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
                    <button key={s} className="suggestion" onClick={() => usarSugerencia(s)}>
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
                    <button key={s} className="suggestion" onClick={() => usarSugerencia(s)}>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              mensajes.map((msg, i) => {
                const esBot = msg.origen !== 'EMPLEADO';
                const fuentes = esBot && msg.fuentes ? String(msg.fuentes).split(',') : [];
                return (
                  <div
                    key={msg.id || i}
                    className={`message ${esBot ? 'bot' : 'user'} fade-in`}
                    style={{ animationDelay: `${i * 0.03}s` }}
                  >
                    <div className="msg-avatar">
                      {esBot ? 'A' : (user?.nombre?.charAt(0) || 'U')}
                    </div>
                    <div className="msg-content">
                      <div className="msg-header">
                        <span className="msg-author">
                          {esBot ? 'Aura' : (msg.usuario?.nombre || user?.nombre)}
                        </span>
                        <span className="msg-time">{formatTime(msg.timestamp)}</span>
                        {esBot && (
                          <button
                            className="msg-copy"
                            onClick={() => copiar(msg.contenido)}
                            title="Copiar respuesta"
                          >
                            {copiado === msg.contenido ? 'Copiado' : 'Copiar'}
                          </button>
                        )}
                      </div>
                      <div className="msg-bubble">
                        {esBot ? <Respuesta texto={msg.contenido} /> : msg.contenido}
                      </div>
                      {fuentes.length > 0 && (
                        <div className="msg-fuentes">
                          <span className="fuentes-label">Fuente:</span>
                          {fuentes.map((f) => (
                            <span className="fuente-chip" key={f}>{f}</span>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                );
              })
            )}

            {loading && (
              <div className="message bot fade-in">
                <div className="msg-avatar">A</div>
                <div className="msg-content">
                  <div className="msg-header"><span className="msg-author">Aura</span></div>
                  <div className="msg-bubble typing">
                    <span /><span /><span />
                  </div>
                  <div className="typing-info">
                    <span className="typing-text">
                      Redactando…{esperando > 0 ? ` ${esperando} s` : ''}
                    </span>
                    <button className="btn-cancelar" onClick={() => abortRef.current?.abort()}>
                      Cancelar
                    </button>
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
                onClick={() => enviar()}
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
