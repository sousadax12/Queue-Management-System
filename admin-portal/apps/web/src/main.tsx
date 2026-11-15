import '@mantine/core/styles.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App, Shell } from './App';
import { AuthProvider } from './auth/AuthProvider';
import { readBrowserConfig } from './config';
import { initFirebase } from './firebase';
import { ConfigErrorPage } from './pages/ConfigErrorPage';

const root = document.getElementById('root');
if (!root) throw new Error('#root is missing from index.html');

const result = readBrowserConfig();
let content: React.ReactNode;
if (result.ok) {
  const { auth } = initFirebase(result.config);
  content = (
    <AuthProvider auth={auth} baseUrl={result.config.baseUrl}>
      <App />
    </AuthProvider>
  );
} else {
  content = <ConfigErrorPage missing={result.missing} />;
}

createRoot(root).render(
  <StrictMode>
    <Shell>{content}</Shell>
  </StrictMode>,
);
