import React from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

function App() {
  return <main><p className="eyebrow">JobMatch México</p><h1>Encuentra trabajo con evidencia.</h1><p>La plataforma está lista para construir el flujo de identidad.</p><span>Fundaciones operativas · Fase 0</span></main>;
}

createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);

