import {
  type Auth,
  GoogleAuthProvider,
  createUserWithEmailAndPassword,
  onIdTokenChanged,
  sendEmailVerification,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
} from 'firebase/auth';
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { resolveAccess } from './access';
import { type AuthActions, AuthContext, type AuthState } from './context';

/** Firebase Auth behind the portal's AuthContext. Sign-in uses popups (design P2). */
export function AuthProvider({
  auth,
  baseUrl,
  children,
}: {
  auth: Auth;
  /** Where verification and password-reset emails send the user back to. */
  baseUrl: string;
  children: ReactNode;
}) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });
  const generation = useRef(0);

  useEffect(
    () =>
      onIdTokenChanged(auth, (user) => {
        const gen = ++generation.current;
        if (!user) {
          setState({ status: 'signedOut' });
          return;
        }
        user.getIdTokenResult().then(
          (token) => {
            if (gen !== generation.current) return;
            setState({
              status: 'signedIn',
              user: { uid: user.uid, email: user.email, displayName: user.displayName },
              access: resolveAccess(token.claims),
            });
          },
          (err: unknown) => {
            console.error('Could not read the ID token', err);
            if (gen === generation.current) void signOut(auth);
          },
        );
      }),
    [auth],
  );

  const actions = useMemo<AuthActions>(() => {
    const continueUrl = { url: `${baseUrl}/sign-in` };
    return {
      async signInWithGoogle() {
        const provider = new GoogleAuthProvider();
        provider.setCustomParameters({ prompt: 'select_account' });
        await signInWithPopup(auth, provider);
      },
      async signInWithPassword(email, password) {
        await signInWithEmailAndPassword(auth, email, password);
      },
      async signUpWithPassword(email, password) {
        const { user } = await createUserWithEmailAndPassword(auth, email, password);
        await sendEmailVerification(user, continueUrl);
      },
      async sendPasswordReset(email) {
        await sendPasswordResetEmail(auth, email, continueUrl);
      },
      async sendVerificationEmail() {
        if (auth.currentUser) await sendEmailVerification(auth.currentUser, continueUrl);
      },
      async refresh() {
        const user = auth.currentUser;
        if (!user) return;
        await user.reload();
        // Fires onIdTokenChanged with the new claims.
        await user.getIdToken(true);
      },
      async signOut() {
        await signOut(auth);
      },
    };
  }, [auth, baseUrl]);

  const value = useMemo(() => ({ state, ...actions }), [state, actions]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
