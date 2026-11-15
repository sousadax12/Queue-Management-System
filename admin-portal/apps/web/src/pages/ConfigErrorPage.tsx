import { Code, List, Text } from '@mantine/core';
import { AuthCard } from './AuthCard';

export function ConfigErrorPage({ missing }: { missing: string[] }) {
  return (
    <AuthCard title="Portal not configured">
      <Text size="sm">These settings are missing or invalid:</Text>
      <List size="sm">
        {missing.map((name) => (
          <List.Item key={name}>
            <Code>{name}</Code>
          </List.Item>
        ))}
      </List>
      <Text size="sm" c="dimmed">
        Set them in <Code>.env.local</Code> for development, or as container environment variables.
      </Text>
    </AuthCard>
  );
}
