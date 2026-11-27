import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './Navbar.css';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const isActive = (path) => location.pathname === path;

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <div className="brand-mark-sm">A</div>
        <span className="navbar-title">Aura</span>
      </div>

      <div className="navbar-links">
        {user?.rol === 'ADMIN' && (
          <Link to="/admin" className={`nav-link ${isActive('/admin') ? 'active' : ''}`}>
            Panel Admin
          </Link>
        )}
        <Link to="/chat" className={`nav-link ${isActive('/chat') ? 'active' : ''}`}>
          Chat
        </Link>
      </div>

      <div className="navbar-user">
        <div className="user-info">
          <span className="user-name">{user?.nombre}</span>
          <span className="user-role">{user?.rol}</span>
        </div>
        <button className="btn-logout" onClick={handleLogout}>
          Salir
        </button>
      </div>
    </nav>
  );
}
