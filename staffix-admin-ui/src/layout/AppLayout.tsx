import { Layout, Menu, Typography } from 'antd'
import { AppstoreOutlined } from '@ant-design/icons'
import { Outlet, useNavigate } from 'react-router'

const { Header, Sider, Content } = Layout

export function AppLayout() {
  const navigate = useNavigate()
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center' }}>
        <Typography.Title level={4} style={{ color: 'white', margin: 0 }}>
          staffix admin
        </Typography.Title>
      </Header>
      <Layout>
        <Sider width={200} theme="light">
          <Menu
            mode="inline"
            defaultSelectedKeys={['overview']}
            items={[{ key: 'overview', icon: <AppstoreOutlined />, label: 'Overview', onClick: () => navigate('/') }]}
          />
        </Sider>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
