/**
 * Signs in through the real UI and Firebase Auth SDK against the Auth emulator, and checks
 * that a user without a role sees "no access", an unverified password user must verify
 * first, and a platform admin (claim set as grant-platform-admin does) sees the dashboard.
 */
import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { signOut } from 'firebase/auth';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { App, Shell } from '../../src/App';
import { AuthProvider } from '../../src/auth/AuthProvider';
import { readConfig } from '../../src/config';
import { initFirebase } from '../../src/firebase';

const host = process.env.FIREBASE_AUTH_EMULATOR_HOST;
if (!host)
  throw new Error('Run emulator tests with `pnpm test:emulator` (firebase emulators:exec).');
const PROJECT = 'demo-qms';
const API_KEY = 'demo-key';
const emulator = `http://${host}`;

const result = readConfig(
  [
    {
      VITE_FIREBASE_API_KEY: API_KEY,
      VITE_FIREBASE_AUTH_DOMAIN: `${PROJECT}.firebaseapp.com`,
      VITE_FIREBASE_PROJECT_ID: PROJECT,
      VITE_FIREBASE_APP_ID: 'demo-app',
      VITE_FIREBASE_AUTH_EMULATOR_URL: emulator,
    },
  ],
  'http://localhost:5173',
);
if (!result.ok) throw new Error(`bad test config: ${result.missing.join(', ')}`);
const config = result.config;
const { auth } = initFirebase(config);

async function emulatorApi(path: string, body?: unknown, method = 'POST'): Promise<unknown> {
  const res = await fetch(`${emulator}${path}`, {
    method,
    headers: { 'content-type': 'application/json', authorization: 'Bearer owner' },
    ...(body !== undefined && { body: JSON.stringify(body) }),
  });
  if (!res.ok) throw new Error(`${path}: ${res.status} ${await res.text()}`);
  return res.json();
}

async function createUser(email: string, password: string): Promise<string> {
  const r = (await emulatorApi(
    `/identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}`,
    {
      email,
      password,
      returnSecureToken: true,
    },
  )) as { localId: string };
  return r.localId;
}

/** What the Admin SDK's updateUser / setCustomUserClaims do, through the emulator's REST API. */
async function updateUser(
  uid: string,
  patch: { emailVerified?: boolean; claims?: Record<string, unknown> },
): Promise<void> {
  await emulatorApi(`/identitytoolkit.googleapis.com/v1/projects/${PROJECT}/accounts:update`, {
    localId: uid,
    ...(patch.emailVerified !== undefined && { emailVerified: patch.emailVerified }),
    ...(patch.claims && { customAttributes: JSON.stringify(patch.claims) }),
  });
}

function renderApp() {
  window.history.replaceState(null, '', '/');
  render(
    <Shell>
      <AuthProvider auth={auth} baseUrl={config.baseUrl}>
        <App />
      </AuthProvider>
    </Shell>,
  );
}

async function signInThroughUi(email: string, password: string) {
  await userEvent.type(await screen.findByLabelText('Email'), email);
  await userEvent.type(screen.getByLabelText('Password'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

beforeEach(async () => {
  await emulatorApi(`/emulator/v1/projects/${PROJECT}/accounts`, undefined, 'DELETE');
});
afterEach(async () => {
  await signOut(auth);
});

describe('sign-in against the Auth emulator', () => {
  it('shows "no access" without a role, and the dashboard once platformAdmin is granted', async () => {
    const uid = await createUser('ana@example.com', 'correct-horse');
    await updateUser(uid, { emailVerified: true });
    renderApp();

    await signInThroughUi('ana@example.com', 'correct-horse');
    expect(await screen.findByRole('heading', { name: 'No access' })).toBeInTheDocument();
    expect(screen.getByText(uid)).toBeInTheDocument();

    await updateUser(uid, { claims: { role: 'platformAdmin', org: null, sites: [] } });
    await userEvent.click(screen.getByRole('button', { name: 'Check again' }));
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getByText('Platform admin')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('makes an unverified password user verify their email, even with a role', async () => {
    const uid = await createUser('bo@example.com', 'correct-horse');
    await updateUser(uid, { claims: { role: 'platformAdmin', org: null, sites: [] } });
    renderApp();

    await signInThroughUi('bo@example.com', 'correct-horse');
    expect(await screen.findByRole('heading', { name: 'Verify your email' })).toBeInTheDocument();

    await updateUser(uid, { emailVerified: true });
    await userEvent.click(screen.getByRole('button', { name: "I've verified" }));
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
  });

  it('rejects a wrong password', async () => {
    await createUser('cy@example.com', 'correct-horse');
    renderApp();
    await signInThroughUi('cy@example.com', 'wrong-horse');
    expect(await screen.findByText('Wrong email or password.')).toBeInTheDocument();
  });
});
