import { Alert, Skeleton, Typography } from 'antd'
import { useSessionSettings, type SessionRef } from '../api/queries'

export function SessionSettingsTab({ sessionRef, runningConfigId }: { sessionRef: SessionRef; runningConfigId: string }) {
  const settings = useSessionSettings(sessionRef, runningConfigId)
  if (settings.isPending) return <Skeleton active />
  if (settings.isError) return <Alert type="error" title={settings.error.message} />
  const json = JSON.stringify(settings.data, null, 2)
  return (
    <>
      <Typography.Paragraph type="secondary">Settings of the selected config, {runningConfigId}.</Typography.Paragraph>
      <Typography.Paragraph copyable={{ text: json }} style={{ margin: 0 }}>
        <pre style={{ maxHeight: '70vh', overflow: 'auto', margin: 0 }}>{json}</pre>
      </Typography.Paragraph>
    </>
  )
}
