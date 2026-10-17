import { useState, useEffect, useRef } from 'react';
import Navbar from '../components/Navbar';
import api from '../api/axios';
import './AdminPanel.css';

export default function AdminPanel() {
  const [tab, setTab] = useState('documentos');
  const [documentos, setDocumentos] = useState([]);
  const [usuarios, setUsuarios] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [borrando, setBorrando] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [msg, setMsg] = useState('');
  const [msgType, setMsgType] = useState('');
  const fileRef = useRef();

  // Formulario nuevo usuario
  const [userForm, setUserForm] = useState({
    nombre: '', password: '', rol: 'EMPLEADO',
    contacto: { email: '', telefono: '' }
  });
  const [userLoading, setUserLoading] = useState(false);

  useEffect(() => {
    cargarDocumentos();
    cargarUsuarios();
  }, []);

  const notify = (text, type = 'success') => {
    setMsg(text); setMsgType(type);
    setTimeout(() => setMsg(''), 3500);
  };

  // Los errores de validacion llegan como {"campo": "mensaje"}, y los de la IA
  // como {"detail": "..."}: se muestra el primero que exista en vez de un texto
  // generico que oculta el motivo real (por ejemplo, un PDF sin texto).
  const motivo = (err, porDefecto) => {
    const data = err.response?.data || {};
    return data.detail || data.error || Object.values(data)[0] || porDefecto;
  };

  const cargarDocumentos = async () => {
    try {
      const { data } = await api.get('/documentos');
      setDocumentos(data);
    } catch { notify('Error al cargar documentos', 'error'); }
    finally { setCargando(false); }
  };

  const cargarUsuarios = async () => {
    try {
      const { data } = await api.get('/usuarios');
      setUsuarios(data);
    } catch { notify('Error al cargar usuarios', 'error'); }
    finally { setCargando(false); }
  };

  const subirArchivo = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (!file.name.endsWith('.pdf')) {
      notify('Solo se aceptan archivos PDF', 'error'); return;
    }
    setUploading(true);
    const formData = new FormData();
    formData.append('archivo', file);
    try {
      await api.post('/documentos/subir', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      notify('Documento subido correctamente');
      cargarDocumentos();
    } catch (err) { notify(motivo(err, 'Error al subir el documento'), 'error'); }
    finally { setUploading(false); fileRef.current.value = ''; }
  };

  const eliminarDoc = async (doc) => {
    // El confirm tiene que decir CUAL documento: con un texto generico es
    // facil borrar el de al lado, y esto no se puede deshacer.
    if (!window.confirm(`¿Eliminar "${doc.nombreArchivo}"? No se puede deshacer.`)) return;
    setBorrando(doc.id);
    try {
      await api.delete(`/documentos/${doc.id}`);
      notify('Documento eliminado');
      cargarDocumentos();
    } catch (err) { notify(motivo(err, 'Error al eliminar'), 'error'); }
    finally { setBorrando(null); }
  };

  const handleUserForm = (e) => {
    const { name, value } = e.target;
    if (name === 'email' || name === 'telefono') {
      setUserForm({ ...userForm, contacto: { ...userForm.contacto, [name]: value } });
    } else {
      setUserForm({ ...userForm, [name]: value });
    }
  };

  const crearUsuario = async (e) => {
    e.preventDefault();
    if (userForm.password.length < 8) {
      notify('La contraseña debe tener al menos 8 caracteres', 'error'); return;
    }
    setUserLoading(true);
    try {
      await api.post('/usuarios', userForm);
      notify('Usuario creado correctamente');
      setUserForm({ nombre: '', password: '', rol: 'EMPLEADO', contacto: { email: '', telefono: '' } });
      cargarUsuarios();
    } catch (err) {
      notify(motivo(err, 'Error al crear usuario'), 'error');
    } finally { setUserLoading(false); }
  };

  const desactivarUsuario = async (id) => {
    if (!window.confirm('¿Desactivar este usuario?')) return;
    try {
      await api.delete(`/usuarios/${id}`);
      notify('Usuario desactivado');
      cargarUsuarios();
    } catch { notify('Error al desactivar', 'error'); }
  };

  const activarUsuario = async (id) => {
    try {
      await api.patch(`/usuarios/${id}/activar`);
      notify('Usuario activado');
      cargarUsuarios();
    } catch { notify('Error al activar', 'error'); }
  };

  return (
    <div className="admin-layout">
      <Navbar />

      {msg && (
        <div className={`toast toast-${msgType}`}>{msg}</div>
      )}

      <div className="admin-content fade-in">
        <div className="admin-header">
          <div>
            <h1 className="page-title">Panel de administración</h1>
            <p className="page-sub">Gestioná documentos y usuarios del sistema</p>
          </div>
        </div>

        {/* Tabs */}
        <div className="tabs">
          <button
            className={`tab ${tab === 'documentos' ? 'active' : ''}`}
            onClick={() => setTab('documentos')}
          >
            Documentos
            <span className="tab-count">{documentos.length}</span>
          </button>
          <button
            className={`tab ${tab === 'usuarios' ? 'active' : ''}`}
            onClick={() => setTab('usuarios')}
          >
            Usuarios
            <span className="tab-count">{usuarios.length}</span>
          </button>
        </div>

        {/* DOCUMENTOS */}
        {tab === 'documentos' && (
          <div className="tab-content slide-in">
            {/* Upload */}
            <div className="upload-zone" onClick={() => fileRef.current.click()}>
              <input
                ref={fileRef}
                type="file"
                accept=".pdf"
                onChange={subirArchivo}
                style={{ display: 'none' }}
              />
              {uploading ? (
                <>
                  <span className="spinner" />
                  <span className="upload-text">Procesando documento...</span>
                </>
              ) : (
                <>
                  <span className="upload-icon">↑</span>
                  <span className="upload-text">
                    Subir un documento
                    <span className="upload-hint">
                      Solo archivos .pdf · se procesa y se indexa (puede tardar unos segundos)
                    </span>
                  </span>
                  <span className="upload-btn">Elegir archivo</span>
                </>
              )}
            </div>

            {/* Lista */}
            <div className="list-header">
              <span>Documentos cargados</span>
            </div>

            {cargando ? (
              <div className="empty-state">Cargando documentos...</div>
            ) : documentos.length === 0 ? (
              <div className="empty-state">
                No hay documentos cargados todavía
              </div>
            ) : (
              <div className="doc-list">
                {documentos.map((doc) => (
                  <div key={doc.id} className="doc-item">
                    <div className="doc-icon">PDF</div>
                    <div className="doc-info">
                      <span className="doc-name">{doc.nombreArchivo}</span>
                      <span className="doc-meta">
                        Subido el {new Date(doc.fechaSubida).toLocaleDateString('es-AR')}
                        {' · '}por {doc.admin?.nombre || 'Admin'}
                      </span>
                    </div>
                    <button
                      className="btn-danger"
                      onClick={() => eliminarDoc(doc)}
                      disabled={borrando === doc.id}
                    >
                      {borrando === doc.id ? 'Eliminando...' : 'Eliminar'}
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* USUARIOS */}
        {tab === 'usuarios' && (
          <div className="tab-content slide-in">
            {/* Formulario nuevo usuario */}
            <div className="form-card">
              <h3 className="form-title">Nuevo usuario</h3>
              <form onSubmit={crearUsuario} className="user-form">
                <div className="field">
                  <label>Nombre</label>
                  <input name="nombre" value={userForm.nombre} onChange={handleUserForm} placeholder="Juan Pérez" required />
                </div>
                <div className="field">
                  <label>Email</label>
                  <input type="email" name="email" value={userForm.contacto.email} onChange={handleUserForm} placeholder="juan@empresa.com" required />
                </div>
                <div className="field">
                  <label>Teléfono</label>
                  <input name="telefono" value={userForm.contacto.telefono} onChange={handleUserForm} placeholder="1111111111" />
                </div>
                <div className="field">
                  <label>Contraseña</label>
                  <input type="password" name="password" value={userForm.password} onChange={handleUserForm} placeholder="Mínimo 8 caracteres" required />
                </div>
                <div className="field">
                  <label>Rol</label>
                  <select name="rol" value={userForm.rol} onChange={handleUserForm}>
                    <option value="EMPLEADO">Empleado</option>
                    <option value="ADMIN">Administrador</option>
                  </select>
                </div>
                <button className="btn-primary" disabled={userLoading}>
                  {userLoading ? <span className="spinner" /> : 'Crear usuario'}
                </button>
              </form>
            </div>

            {/* Lista usuarios */}
            <div className="list-header">
              <span>Usuarios registrados</span>
            </div>

            {cargando ? (
              <div className="empty-state">Cargando usuarios...</div>
            ) : usuarios.length === 0 ? (
              <div className="empty-state">No hay usuarios registrados</div>
            ) : (
              <div className="users-table">
                <div className="users-head">
                  <span>Usuario</span>
                  <span>Rol</span>
                  <span>Estado</span>
                  <span>Acciones</span>
                </div>
                {usuarios.map((u) => (
                  <div key={u.id} className={`user-item ${!u.activo ? 'inactive' : ''}`}>
                    <div className="user-cell">
                      <div className="user-avatar">
                        {u.nombre?.charAt(0).toUpperCase()}
                      </div>
                      <div className="user-info-card">
                        <span className="user-name-card">{u.nombre}</span>
                        <span className="user-email">{u.contacto?.email}</span>
                      </div>
                    </div>
                    <span className={`role-badge role-${u.rol?.toLowerCase()}`}>
                      {u.rol}
                    </span>
                    <span className={`status-badge ${u.activo ? 'active' : 'inactive'}`}>
                      {u.activo ? 'Activo' : 'Inactivo'}
                    </span>
                    <div className="user-actions">
                      {u.activo ? (
                        <button className="btn-danger" onClick={() => desactivarUsuario(u.id)}>
                          Desactivar
                        </button>
                      ) : (
                        <button className="btn-secondary" onClick={() => activarUsuario(u.id)}>
                          Activar
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
