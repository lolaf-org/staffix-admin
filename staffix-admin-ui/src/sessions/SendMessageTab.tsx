import { useState } from 'react'
import { Alert, Button, Checkbox, Flex, Form, Input, Radio, Typography } from 'antd'
import { api, call, type Schemas } from '../api/client'
import { sessionPath, useEngineMutation, type DecodeTarget, type SessionRef } from '../api/queries'
import { DecodedMessageModal } from '../telemetry/DecodedMessageModal'
import { WriteButton } from './WriteButton'

const SOH = '\u0001'

interface MessageForm {
  message: string
  separator: string
  possDup: boolean
}

export function SendMessageTab({ sessionRef, summary }: { sessionRef: SessionRef; summary: Schemas['SessionSummary'] }) {
  const [form] = Form.useForm<MessageForm>()
  const [decoding, setDecoding] = useState<DecodeTarget>()
  const send = useEngineMutation(
    sessionRef.engineId,
    (body: MessageForm) => call(api.POST('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/messages', { ...sessionPath(sessionRef), body })),
    'Message sent',
  )

  return (
    <Form<MessageForm> form={form} layout="vertical" initialValues={{ message: '', separator: '|', possDup: false }}>
      {!summary.loggedIn && <Alert type="warning" title="The session is not logged in: the engine will refuse the message." style={{ marginBottom: 16 }} />}
      <Typography.Paragraph type="secondary">
        The engine fills in the header (BeginString, BodyLength, MsgSeqNum, SenderCompID, TargetCompID, SendingTime)
        and the trailer.
      </Typography.Paragraph>
      <Form.Item name="message" label="Message" rules={[{ required: true, whitespace: true }]}>
        <Input.TextArea rows={6} placeholder="35=D|11=ORDER-1|55=AAPL|54=1|38=100|40=1" style={{ fontFamily: 'monospace' }} />
      </Form.Item>
      <Form.Item name="separator" label="Field separator">
        <Radio.Group
          options={[
            { value: '|', label: '|' },
            { value: '^', label: '^' },
            { value: SOH, label: 'SOH' },
          ]}
        />
      </Form.Item>
      <Form.Item name="possDup" valuePropName="checked" extra="Sends it as a duplicate: its SendingTime(52) becomes OrigSendingTime(122).">
        <Checkbox>PossDup</Checkbox>
      </Form.Item>
      <Flex gap={8}>
        <Button
          onClick={() =>
            form.validateFields(['message']).then(() =>
              setDecoding({
                ref: { ...sessionRef, sessionId: summary.runningConfigId },
                message: form.getFieldValue('message'),
                separator: form.getFieldValue('separator'),
              }),
            )
          }
        >
          Decode
        </Button>
        <WriteButton
          type="primary"
          loading={send.isPending}
          confirm={`Send this message on ${summary.group}.${summary.id}, config ${summary.runningConfigId}?`}
          onConfirm={() => form.validateFields().then((values) => send.mutate(values))}
        >
          Send
        </WriteButton>
      </Flex>
      <DecodedMessageModal target={decoding} onClose={() => setDecoding(undefined)} />
    </Form>
  )
}
