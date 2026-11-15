import { createMemoryHistory } from '@tanstack/react-router';
import { render, screen, waitFor } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AppRouterView, Shell } from '../src/App';
import type { Access } from '../src/auth/access';
import { type AuthActions, AuthContext, type AuthState } from '../src/auth/context';
import { safeRedirect } from '../src/auth/guard';
import { createAppRouter } from '../src/router';

function actions(): AuthActions {
  return {
    signInWithGoogle: vi.fn(() => Promise.resolve()),
    signInWithPassword: vi.fn(() => Promise.resolve()),
    signUpWithPassword: vi.fn(() => Promise.resolve()),
    sendPasswordReset: vi.fn(() => Promise.resolve()),
    sendVerificationEmail: vi.fn(() => Promise.resolve()),
    refresh: vi.fn(() => Promise.resolve()),
    signOut: vi.fn(() => Promise.resolve()),
  };
}

const user = { uid: 'uid_1', email: 'ana@example.com', displayName: 'Ana' };
const signedIn = (access: Access): AuthState => ({ status: 'signedIn', user, access });

function renderAt(path: string, state: AuthState, acts = actions()) {
  const router = createAppRouter(createMemoryHistory({ initialEntries: [path] }));
  render(
    <Shell>
      <AuthContext.Provider value={{ state, ...acts }}>
        <AppRouterView router={router} />
      </AuthContext.Provider>
    </Shell>,
  );
  return { router, acts };
}

describe('route guard', () => {
  it('sends a signed-out user to sign-in, remembering where they were going', async () => {
    const { router } = renderAt('/', { status: 'signedOut' });
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/sign-in');
    expect(router.state.location.search).toEqual({ redirect: '/' });
  });

  it('shows the dashboard to a platform admin', async () => {
    renderAt(
      '/',
      signedIn({ kind: 'staff', claims: { role: 'platformAdmin', org: null, sites: [] } }),
    );
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getByText('Platform admin')).toBeInTheDocument();
  });

  it('shows "no access" to a signed-in user without a role, and lets them re-check', async () => {
    const { router, acts } = renderAt('/', signedIn({ kind: 'noRole' }));
    expect(await screen.findByRole('heading', { name: 'No access' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/no-access');
    expect(screen.getByText('uid_1')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Check again' }));
    expect(acts.refresh).toHaveBeenCalled();
  });

  it('asks a password user to verify their email first', async () => {
    const { router } = renderAt('/', signedIn({ kind: 'unverified' }));
    expect(await screen.findByRole('heading', { name: 'Verify your email' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/verify-email');
  });

  it('moves a staff user off /sign-in and /no-access', async () => {
    const staff = signedIn({
      kind: 'staff',
      claims: { role: 'orgAdmin', org: 'org_a', sites: [] },
    });
    const { router } = renderAt('/no-access', staff);
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });
});

describe('sign-in page', () => {
  it('signs in with email and password', async () => {
    const { acts } = renderAt('/sign-in', { status: 'signedOut' });
    await userEvent.type(await screen.findByLabelText('Email'), 'ana@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'secret-pass');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => {
      expect(acts.signInWithPassword).toHaveBeenCalledWith('ana@example.com', 'secret-pass');
    });
  });

  it('signs in with a Google popup', async () => {
    const { acts } = renderAt('/sign-in', { status: 'signedOut' });
    await userEvent.click(await screen.findByRole('button', { name: 'Continue with Google' }));
    expect(acts.signInWithGoogle).toHaveBeenCalled();
  });

  it('shows Firebase errors in plain words', async () => {
    const acts = actions();
    acts.signInWithPassword = vi.fn(() =>
      Promise.reject(Object.assign(new Error('x'), { code: 'auth/invalid-credential' })),
    );
    renderAt('/sign-in', { status: 'signedOut' }, acts);
    await userEvent.type(await screen.findByLabelText('Email'), 'ana@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'nope');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Wrong email or password.')).toBeInTheDocument();
  });
});

describe('safeRedirect', () => {
  it('only allows same-origin paths', () => {
    expect(safeRedirect('/orgs?x=1')).toBe('/orgs?x=1');
    expect(safeRedirect('//evil.example')).toBe('/');
    expect(safeRedirect('https://evil.example')).toBe('/');
    expect(safeRedirect(undefined)).toBe('/');
  });
});
