import { type FirebaseApp, getApps, initializeApp } from 'firebase/app';
import { type Auth, connectAuthEmulator, getAuth } from 'firebase/auth';
import type { AppConfig } from './config';

export function initFirebase(
  config: AppConfig,
  name = '[DEFAULT]',
): { app: FirebaseApp; auth: Auth } {
  const app = getApps().find((a) => a.name === name) ?? initializeApp(config.firebase, name);
  const auth = getAuth(app);
  if (config.authEmulatorUrl && !auth.emulatorConfig) {
    connectAuthEmulator(auth, config.authEmulatorUrl, { disableWarnings: true });
  }
  return { app, auth };
}
