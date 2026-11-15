import { Center, Loader, MantineProvider } from '@mantine/core';
import { RouterProvider } from '@tanstack/react-router';
import { useEffect, useState } from 'react';
import { useAuth } from './auth/context';
import { type AppRouter, createAppRouter } from './router';
import { theme } from './theme';

/** The router, re-running its guards whenever the auth state changes. */
export function AppRouterView({ router }: { router: AppRouter }) {
  const { state } = useAuth();
  useEffect(() => {
    void router.invalidate();
  }, [router, state]);
  if (state.status === 'loading') {
    return (
      <Center h="100vh">
        <Loader aria-label="Loading" />
      </Center>
    );
  }
  return <RouterProvider router={router} context={{ auth: state }} />;
}

export function Shell({ children }: { children: React.ReactNode }) {
  return <MantineProvider theme={theme}>{children}</MantineProvider>;
}

export function App() {
  const [router] = useState(() => createAppRouter());
  return <AppRouterView router={router} />;
}
