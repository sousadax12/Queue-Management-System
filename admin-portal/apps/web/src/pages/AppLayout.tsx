import { AppShell, Badge, Burger, Button, Group, NavLink, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { Link, Outlet, getRouteApi } from '@tanstack/react-router';
import { ROLE_LABELS, useAuth } from '../auth/context';

const appRoute = getRouteApi('/app');

/** Header + navbar around every signed-in page. Nav items arrive with their milestones. */
export function AppLayout() {
  const { claims } = appRoute.useRouteContext();
  const auth = useAuth();
  const [opened, { toggle }] = useDisclosure();
  const email = auth.state.status === 'signedIn' ? auth.state.user.email : null;

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="lg"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <Text fw={700}>Queue Management · Admin</Text>
          </Group>
          <Group gap="sm" wrap="nowrap">
            <Badge variant="light">{ROLE_LABELS[claims.role]}</Badge>
            <Text size="sm" c="dimmed" visibleFrom="sm">
              {email}
            </Text>
            <Button size="xs" variant="default" onClick={() => void auth.signOut()}>
              Sign out
            </Button>
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Navbar p="sm">
        <NavLink component={Link} to="/" label="Dashboard" activeOptions={{ exact: true }} />
      </AppShell.Navbar>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
