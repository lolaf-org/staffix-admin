import { useState } from 'react'
import { Alert, Button, Card, Flex, Form, Input, Typography } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router'
import { login } from '../api/queries'
import staffixLogo from '../assets/staffix-logo.png'

interface Credentials {
  username: string
  password: string
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  const submit = async ({ username, password }: Credentials) => {
    setSubmitting(true)
    setError(undefined)
    try {
      await login(username, password)
      queryClient.clear()
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/', { replace: true })
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Flex justify="center" align="center" style={{ minHeight: '100vh', padding: 16 }}>
      <Card style={{ width: 360, maxWidth: '100%' }}>
        <Flex vertical align="center" gap={12} style={{ marginBottom: 24 }}>
          <img src={staffixLogo} alt="" width={96} height={96} />
          <Typography.Title level={3} style={{ margin: 0 }}>
            Staffix admin console
          </Typography.Title>
        </Flex>
        <Form<Credentials> layout="vertical" onFinish={submit} requiredMark={false}>
          <Form.Item name="username" label="Username" rules={[{ required: true }]}>
            <Input prefix={<UserOutlined />} autoComplete="username" autoFocus />
          </Form.Item>
          <Form.Item name="password" label="Password" rules={[{ required: true }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
          </Form.Item>
          {error && <Alert type="error" title={error} style={{ marginBottom: 16 }} />}
          <Button type="primary" htmlType="submit" loading={submitting} block>
            Log in
          </Button>
        </Form>
      </Card>
    </Flex>
  )
}
