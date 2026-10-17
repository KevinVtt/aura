// Configuración mínima de Jest para CRA (solo se carga en tests, no en producción).
// React 18 requiere IS_REACT_ACT_ENVIRONMENT=true para que act() funcione sin warnings.
global.IS_REACT_ACT_ENVIRONMENT = true;

// jsdom 16 no implementa scrollIntoView (Chat.jsx lo usa en un useEffect).
// Es una limitación del entorno de test, no un bug de producción.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = jest.fn();
}