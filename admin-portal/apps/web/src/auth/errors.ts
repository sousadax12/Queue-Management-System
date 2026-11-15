/** A short, user-facing message for a Firebase Auth error. */
export function authErrorMessage(err: unknown): string {
  const code = (err as { code?: unknown } | null)?.code;
  switch (code) {
    case 'auth/invalid-credential':
    case 'auth/wrong-password':
    case 'auth/user-not-found':
    case 'auth/invalid-email':
      return 'Wrong email or password.';
    case 'auth/email-already-in-use':
      return 'An account with this email already exists. Sign in instead.';
    case 'auth/weak-password':
      return 'Choose a stronger password (at least 8 characters).';
    case 'auth/too-many-requests':
      return 'Too many attempts. Wait a moment and try again.';
    case 'auth/popup-closed-by-user':
    case 'auth/cancelled-popup-request':
      return 'The sign-in window was closed.';
    case 'auth/popup-blocked':
      return 'Your browser blocked the sign-in window. Allow popups for this site.';
    case 'auth/network-request-failed':
      return 'Network error. Check your connection.';
    case 'auth/unauthorized-domain':
      return 'This domain is not authorised for sign-in in Firebase.';
    default:
      return 'Something went wrong. Try again.';
  }
}
