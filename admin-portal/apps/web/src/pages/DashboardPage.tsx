import { Paper, Stack, Text, Title } from '@mantine/core';
import { getRouteApi } from '@tanstack/react-router';

const appRoute = getRouteApi('/app');

export function DashboardPage() {
  const { claims } = appRoute.useRouteContext();
  return (
    <Stack>
      <Title order={2}>Dashboard</Title>
      <Paper withBorder p="xl">
        <Text c="dimmed">
          {claims.role === 'platformAdmin'
            ? 'Nothing here yet. Orgs, sites, devices and media arrive in the next milestones.'
            : 'Nothing here yet for your role.'}
        </Text>
      </Paper>
    </Stack>
  );
}
