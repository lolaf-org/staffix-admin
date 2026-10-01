import { Alert, Skeleton, Typography } from 'antd'
import { useSessionSettings } from '../api/queries'

export function SessionSettingsTab({ instanceId, sessionId }: { instanceId: string; sessionId: string }) {
  const settings = useSessionSettings(instanceId, sessionId)
  if (settings.isPending) return <Skeleton active />
  if (settings.isError) return <Alert type="error" title={settings.error.message} />
  const json = JSON.stringify(settings.data, null, 2)
  return (
    <Typography.Paragraph copyable={{ text: json }} style={{ margin: 0 }}>
      <pre style={{ maxHeight: '70vh', overflow: 'auto', margin: 0 }}>{json}</pre>
    </Typography.Paragraph>
  )
}
