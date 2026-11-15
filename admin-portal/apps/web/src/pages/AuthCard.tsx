import { Center, Paper, Stack, Text, Title } from '@mantine/core';
import type { ReactNode } from 'react';

/** The centred card the signed-out and no-access pages share. */
export function AuthCard({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Center mih="100vh" p="md" bg="var(--mantine-color-gray-0)">
      <Paper withBorder shadow="sm" p="xl" w="100%" maw={420}>
        <Stack gap="md">
          <div>
            <Text size="sm" c="dimmed" fw={500}>
              Queue Management · Admin
            </Text>
            <Title order={2}>{title}</Title>
            {subtitle && (
              <Text c="dimmed" size="sm" mt={4}>
                {subtitle}
              </Text>
            )}
          </div>
          {children}
        </Stack>
      </Paper>
    </Center>
  );
}
