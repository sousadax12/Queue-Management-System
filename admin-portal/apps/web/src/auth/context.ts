import type { Role } from '@qms/contract';
import { createContext, useContext } from 'react';
import type { Access } from './access';

export interface SignedInUser {
  uid: string;
  email: string | null;
  displayName: string | null;
}

export type AuthState =
  | { status: 'loading' }
  | { status: 'signedOut' }
  | { status: 'signedIn'; user: SignedInUser; access: Access };

export interface AuthActions {
  signInWithGoogle: () => Promise<void>;
  signInWithPassword: (email: string, password: string) => Promise<void>;
  /** Creates the account and sends the verification email. */
  signUpWithPassword: (email: string, password: string) => Promise<void>;
  sendPasswordReset: (email: string) => Promise<void>;
  sendVerificationEmail: () => Promise<void>;
  /** Reload the user and force a token refresh, to pick up a new role or a verified email. */
  refresh: () => Promise<void>;
  signOut: () => Promise<void>;
}

export interface AuthContextValue extends AuthActions {
  state: AuthState;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth() outside <AuthProvider>');
  return value;
}

export const ROLE_LABELS: Record<Role, string> = {
  platformAdmin: 'Platform admin',
  orgAdmin: 'Org admin',
  manager: 'Site manager',
  operator: 'Operator',
};
