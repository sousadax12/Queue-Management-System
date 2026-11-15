import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Anchor,
  Button,
  Divider,
  Group,
  PasswordInput,
  Stack,
  TextInput,
} from '@mantine/core';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useAuth } from '../auth/context';
import { authErrorMessage } from '../auth/errors';
import { AuthCard } from './AuthCard';

const formSchema = z.object({
  email: z.email('Enter a valid email'),
  password: z.string().min(1, 'Enter your password'),
});
const signUpSchema = formSchema.extend({
  password: z.string().min(8, 'Use at least 8 characters'),
});
type FormValues = z.infer<typeof formSchema>;
type Mode = 'signIn' | 'signUp';

export function SignInPage() {
  const auth = useAuth();
  const [mode, setMode] = useState<Mode>('signIn');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [googleBusy, setGoogleBusy] = useState(false);
  const form = useForm<FormValues>({
    resolver: zodResolver(mode === 'signUp' ? signUpSchema : formSchema),
    defaultValues: { email: '', password: '' },
  });

  const run = async (action: () => Promise<void>) => {
    setError(null);
    setNotice(null);
    try {
      await action();
    } catch (err) {
      setError(authErrorMessage(err));
    }
  };

  const onSubmit = form.handleSubmit((v) =>
    run(() =>
      mode === 'signUp'
        ? auth.signUpWithPassword(v.email, v.password)
        : auth.signInWithPassword(v.email, v.password),
    ),
  );

  const onGoogle = async () => {
    setGoogleBusy(true);
    await run(() => auth.signInWithGoogle());
    setGoogleBusy(false);
  };

  const onReset = async () => {
    const email = form.getValues('email');
    if (!(await form.trigger('email'))) return;
    await run(async () => {
      await auth.sendPasswordReset(email);
      setNotice(`If ${email} has an account, a reset link is on its way.`);
    });
  };

  const { errors, isSubmitting } = form.formState;
  return (
    <AuthCard
      title={mode === 'signUp' ? 'Create an account' : 'Sign in'}
      subtitle="Staff access only. An admin grants your role after you sign in."
    >
      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}
      {notice && (
        <Alert color="blue" variant="light">
          {notice}
        </Alert>
      )}
      <Button variant="default" onClick={() => void onGoogle()} loading={googleBusy}>
        Continue with Google
      </Button>
      <Divider label="or with email" labelPosition="center" />
      <form onSubmit={(e) => void onSubmit(e)} noValidate>
        <Stack gap="sm">
          <TextInput
            label="Email"
            type="email"
            autoComplete="email"
            {...form.register('email')}
            error={errors.email?.message}
          />
          <PasswordInput
            label="Password"
            autoComplete={mode === 'signUp' ? 'new-password' : 'current-password'}
            {...form.register('password')}
            error={errors.password?.message}
          />
          <Button type="submit" loading={isSubmitting}>
            {mode === 'signUp' ? 'Create account' : 'Sign in'}
          </Button>
        </Stack>
      </form>
      <Group justify="space-between">
        <Anchor
          component="button"
          type="button"
          size="sm"
          onClick={() => {
            setMode(mode === 'signUp' ? 'signIn' : 'signUp');
            setError(null);
            form.clearErrors();
          }}
        >
          {mode === 'signUp' ? 'I have an account' : 'Create an account'}
        </Anchor>
        {mode === 'signIn' && (
          <Anchor component="button" type="button" size="sm" onClick={() => void onReset()}>
            Forgot password?
          </Anchor>
        )}
      </Group>
    </AuthCard>
  );
}
