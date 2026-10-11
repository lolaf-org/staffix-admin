/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Badge, Button, Flex, Layout, Segmented, Spin, Tabs, Tag, Tooltip, Typography, theme } from 'antd'
import { ApiOutlined, AppstoreOutlined, BellOutlined, AreaChartOutlined, DesktopOutlined, FileSearchOutlined, LogoutOutlined, NodeIndexOutlined, MoonOutlined, SunOutlined } from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router'
import { isUnauthorized } from '../api/client'
import { logout, useAlerts, useCurrentUser } from '../api/queries'
import { useEnginesWithUnknownAlerts } from '../alerts/unknown'
import { useThemeMode, type ThemeMode } from '../theme/ThemeMode'
import staffixLogo from '../assets/staffix-logo.png'

const { Header, Content } = Layout

const TABS = [
  { path: '/', label: 'Sessions', icon: <AppstoreOutlined /> },
  { path: '/alerts', label: 'Alerts', icon: <BellOutlined /> },
  { path: '/metrics', label: 'Metrics', icon: <AreaChartOutlined /> },
  { path: '/logs', label: 'Logs', icon: <FileSearchOutlined /> },
  { path: '/trace', label: 'Trace', icon: <NodeIndexOutlined /> },
  { path: '/connectors', label: 'Connectors', icon: <ApiOutlined /> },
]

export function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const me = useCurrentUser()
  const themeMode = useThemeMode()
  const alerts = useAlerts()
  const unknownEngines = useEnginesWithUnknownAlerts()
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
          <Flex align="center" gap={12}>
            <img src={staffixLogo} alt="" width={52} height={52} />
            <Typography.Title level={4} style={{ color: 'white', margin: 0, whiteSpace: 'nowrap' }}>
              Staffix admin console
            </Typography.Title>
          </Flex>
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
          items={TABS.map((tab) => ({
            key: tab.path,
            icon: tab.icon,
            label: tab.path === '/alerts' ? <AlertsTabLabel count={alerts.data?.alerts.length ?? 0} unknown={unknownEngines.size > 0} /> : tab.label,
          }))}
          tabBarStyle={{ margin: 0, paddingInline: 20, background: token.colorBgContainer }}
        />
      </div>
      <Content style={{ padding: 16 }}>
        <Outlet />
      </Content>
    </Layout>
  )
}

/**
 * The count leaves out the engines that do not answer, so a "?" says it may be short.
 */
function AlertsTabLabel({ count, unknown }: { count: number, unknown: boolean }) {
  const { token } = theme.useToken()
  return (
    <Tooltip title={unknown ? 'Alerts unknown for an engine that does not answer' : undefined}>
      <span>
        <Badge count={count} size="small" offset={[8, -2]}>
          Alerts
        </Badge>
        {unknown && (
          <span aria-label="Alerts unknown for an engine" style={{ marginLeft: count > 0 ? 14 : 6 }}>
            <Badge count="?" size="small" color={token.colorTextQuaternary} />
          </span>
        )}
      </span>
    </Tooltip>
  )
}
