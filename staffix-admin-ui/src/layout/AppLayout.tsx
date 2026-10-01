import { Button, Flex, Layout, Menu, Spin, Tag, Typography } from 'antd'
import { AppstoreOutlined, FileSearchOutlined, LogoutOutlined } from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router'
import { isUnauthorized } from '../api/client'
import { logout, useCurrentUser } from '../api/queries'

const { Header, Sider, Content } = Layout

export function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const me = useCurrentUser()

  if (me.isPending) {
    return <Spin fullscreen />
  }
  if (me.isError) {
    if (isUnauthorized(me.error)) {
      return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
    }
    throw me.error
  }

  const signOut = async () => {
    await logout()
    queryClient.clear()
    navigate('/login')
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
        <Typography.Title level={4} style={{ color: 'white', margin: 0, whiteSpace: 'nowrap' }}>
          staffix admin
        </Typography.Title>
        <Flex align="center" gap={8}>
          <Typography.Text style={{ color: 'white' }}>{me.data.username}</Typography.Text>
          <Tag color={me.data.roles.includes('WRITER') ? 'blue' : 'default'}>
            {me.data.roles.includes('WRITER') ? 'writer' : 'reader'}
          </Tag>
          <Button icon={<LogoutOutlined />} onClick={signOut} aria-label="Log out" />
        </Flex>
      </Header>
      <Layout>
        <Sider width={180} theme="light" breakpoint="md" collapsedWidth={0}>
          <Menu
            mode="inline"
            selectedKeys={[location.pathname.startsWith('/logs') ? 'logs' : 'sessions']}
            items={[
              { key: 'sessions', icon: <AppstoreOutlined />, label: 'Sessions', onClick: () => navigate('/') },
              { key: 'logs', icon: <FileSearchOutlined />, label: 'Logs', onClick: () => navigate('/logs') },
            ]}
          />
        </Sider>
        <Content style={{ padding: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
