import { Alert, Button, Code, Group, Text } from '@mantine/core';
import { useState } from 'react';
import { useAuth } from '../auth/context';
import { AuthCard } from './AuthCard';

export function NoAccessPage() {
  const auth = useAuth();
  const user = auth.state.status === 'signedIn' ? auth.state.user : null;
  const [checking, setChecking] = useState(false);
  const [stillNone, setStillNone] = useState(false);

  const check = async () => {
    setChecking(true);
    try {
      await auth.refresh();
      setStillNone(true);
    } finally {
      setChecking(false);
    }
  };

  return (
    <AuthCard title="No access" subtitle={user?.email ?? undefined}>
      <Text size="sm">
        Your account has no role in the portal yet. Ask an admin to grant you one, and give them
        your user id:
      </Text>
      <Code block>{user?.uid}</Code>
      {stillNone && (
        <Alert color="yellow" variant="light">
          Still no role on your account.
        </Alert>
      )}
      <Group grow>
        <Button loading={checking} onClick={() => void check()}>
          Check again
        </Button>
        <Button variant="default" onClick={() => void auth.signOut()}>
          Sign out
        </Button>
      </Group>
    </AuthCard>
  );
}
