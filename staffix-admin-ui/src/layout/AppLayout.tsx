import { Button, Flex, Layout, Segmented, Spin, Tabs, Tag, Typography, theme } from 'antd'
import { ApiOutlined, AppstoreOutlined, AreaChartOutlined, DesktopOutlined, FileSearchOutlined, LogoutOutlined, MoonOutlined, SunOutlined } from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router'
import { isUnauthorized } from '../api/client'
import { logout, useCurrentUser } from '../api/queries'
import { useThemeMode, type ThemeMode } from '../theme/ThemeMode'

const { Header, Content } = Layout

const TABS = [
  { path: '/', label: 'Sessions', icon: <AppstoreOutlined /> },
  { path: '/metrics', label: 'Metrics', icon: <AreaChartOutlined /> },
  { path: '/logs', label: 'Logs', icon: <FileSearchOutlined /> },
  { path: '/connectors', label: 'Connectors', icon: <ApiOutlined /> },
]

export function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const me = useCurrentUser()
  const themeMode = useThemeMode()
  const { token } = theme.useToken()

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
      <div style={{ margin: '16px 16px 0', borderRadius: token.borderRadiusLG, overflow: 'hidden', border: `1px solid ${token.colorBorderSecondary}` }}>
        <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, paddingInline: 20 }}>
          <Typography.Title level={4} style={{ color: 'white', margin: 0, whiteSpace: 'nowrap' }}>
            Staffix admin console
          </Typography.Title>
          <Flex align="center" gap={8}>
            <Segmented<ThemeMode>
              size="small"
              value={themeMode.mode}
              onChange={themeMode.setMode}
              options={[
                { value: 'light', icon: <SunOutlined />, title: 'Light' },
                { value: 'dark', icon: <MoonOutlined />, title: 'Dark' },
                { value: 'system', icon: <DesktopOutlined />, title: 'Follow the system' },
              ]}
            />
            <Typography.Text style={{ color: 'white' }}>{me.data.username}</Typography.Text>
            {!me.data.roles.includes('WRITER') && <Tag color="warning">read only</Tag>}
            <Button icon={<LogoutOutlined />} onClick={signOut} aria-label="Log out" />
          </Flex>
        </Header>
        <Tabs
          activeKey={TABS.find((tab) => tab.path !== '/' && location.pathname.startsWith(tab.path))?.path ?? '/'}
          onChange={(path) => navigate(path)}
          items={TABS.map((tab) => ({ key: tab.path, icon: tab.icon, label: tab.label }))}
          tabBarStyle={{ margin: 0, paddingInline: 20, background: token.colorBgContainer }}
        />
      </div>
      <Content style={{ padding: 16 }}>
        <Outlet />
      </Content>
    </Layout>
  )
}
