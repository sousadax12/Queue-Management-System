import {
  type RouterHistory,
  Outlet,
  createRootRouteWithContext,
  createRoute,
  createRouter,
  redirect,
} from '@tanstack/react-router';
import { z } from 'zod';
import type { AuthState } from './auth/context';
import { PORTAL_ROLES, requireRole, safeRedirect } from './auth/guard';
import { AppLayout } from './pages/AppLayout';
import { DashboardPage } from './pages/DashboardPage';
import { NoAccessPage } from './pages/NoAccessPage';
import { SignInPage } from './pages/SignInPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';

export interface RouterContext {
  auth: AuthState;
}

/* eslint-disable @typescript-eslint/only-throw-error -- TanStack Router redirects are thrown */

const rootRoute = createRootRouteWithContext<RouterContext>()({
  component: Outlet,
});

const signInRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/sign-in',
  validateSearch: z.object({ redirect: z.string().optional() }),
  beforeLoad: ({ context, search }) => {
    if (context.auth.status === 'signedIn') throw redirect({ href: safeRedirect(search.redirect) });
  },
  component: SignInPage,
});

const verifyEmailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/verify-email',
  beforeLoad: ({ context: { auth } }) => {
    if (auth.status !== 'signedIn') throw redirect({ to: '/sign-in' });
    if (auth.access.kind !== 'unverified') throw redirect({ to: '/' });
  },
  component: VerifyEmailPage,
});

const noAccessRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/no-access',
  beforeLoad: ({ context: { auth } }) => {
    if (auth.status !== 'signedIn') throw redirect({ to: '/sign-in' });
    if (auth.access.kind === 'unverified') throw redirect({ to: '/verify-email' });
    if (auth.access.kind === 'staff' && PORTAL_ROLES.includes(auth.access.claims.role)) {
      throw redirect({ to: '/' });
    }
  },
  component: NoAccessPage,
});

/** Everything behind sign-in and a role. */
const appRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'app',
  beforeLoad: ({ context, location }) => ({
    claims: requireRole(context.auth, PORTAL_ROLES, location.href),
  }),
  component: AppLayout,
});

const dashboardRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/',
  component: DashboardPage,
});

/* eslint-enable @typescript-eslint/only-throw-error */

export const routeTree = rootRoute.addChildren([
  signInRoute,
  verifyEmailRoute,
  noAccessRoute,
  appRoute.addChildren([dashboardRoute]),
]);

export function createAppRouter(history?: RouterHistory) {
  return createRouter({
    routeTree,
    ...(history && { history }),
    defaultPreload: false,
    // Set by <RouterProvider context> from the AuthProvider.
    context: { auth: { status: 'loading' } },
  });
}

export type AppRouter = ReturnType<typeof createAppRouter>;

declare module '@tanstack/react-router' {
  interface Register {
    router: AppRouter;
  }
}
