import { Alert, Empty, List, Spin } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

export function OverviewPage() {
  const engines = useQuery({
    queryKey: ['engines'],
    queryFn: async () => {
      const { data, error } = await api.GET('/api/engines')
      if (error) throw new Error('Failed to load engines')
      return data
    },
  })

  if (engines.isPending) return <Spin />
  if (engines.isError) return <Alert type="error" title={engines.error.message} />
  if (engines.data.length === 0) return <Empty description="No engine has announced itself yet" />
  return <List dataSource={engines.data} renderItem={(engine) => <List.Item>{engine.instanceId}</List.Item>} />
}
