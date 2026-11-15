import { Alert, Button, Group, Text } from '@mantine/core';
import { useState } from 'react';
import { useAuth } from '../auth/context';
import { authErrorMessage } from '../auth/errors';
import { AuthCard } from './AuthCard';

export function VerifyEmailPage() {
  const auth = useAuth();
  const email = auth.state.status === 'signedIn' ? auth.state.user.email : null;
  const [message, setMessage] = useState<{ color: string; text: string } | null>(null);
  const [busy, setBusy] = useState<'send' | 'check' | null>(null);

  const act = async (kind: 'send' | 'check', fn: () => Promise<void>, done?: string) => {
    setBusy(kind);
    try {
      await fn();
      setMessage(done ? { color: 'blue', text: done } : null);
    } catch (err) {
      setMessage({ color: 'red', text: authErrorMessage(err) });
    } finally {
      setBusy(null);
    }
  };

  return (
    <AuthCard title="Verify your email">
      <Text size="sm">
        We sent a verification link to <b>{email}</b>. Open it, then come back and press{' '}
        <b>I've verified</b>.
      </Text>
      {message && (
        <Alert color={message.color} variant="light">
          {message.text}
        </Alert>
      )}
      <Group grow>
        <Button
          loading={busy === 'check'}
          onClick={() =>
            void act('check', () => auth.refresh(), 'Not verified yet. Open the link in the email.')
          }
        >
          I've verified
        </Button>
        <Button
          variant="default"
          loading={busy === 'send'}
          onClick={() => void act('send', () => auth.sendVerificationEmail(), 'Sent a new link.')}
        >
          Resend link
        </Button>
      </Group>
      <Button variant="subtle" color="gray" onClick={() => void auth.signOut()}>
        Sign out
      </Button>
    </AuthCard>
  );
}
