import { useRef, useState } from 'react'
import { Alert, Button, Checkbox, Flex, Form, Input, Radio, Typography } from 'antd'
import type { TextAreaRef } from 'antd/es/input/TextArea'
import { api, call, type Schemas } from '../api/client'
import { sessionPath, useEngineMutation, type DecodeTarget, type SessionRef } from '../api/queries'
import { DecodedMessageModal } from '../telemetry/DecodedMessageModal'
import { WriteButton } from './WriteButton'

const SOH = '\u0001'

interface MessageForm {
  messages: string
  separator: string
  possDup: boolean
}

type SendRequest = Omit<MessageForm, 'messages'> & { messages: string[] }

export function SendMessageTab({ sessionRef, summary }: { sessionRef: SessionRef; summary: Schemas['SessionSummary'] }) {
  const [form] = Form.useForm<MessageForm>()
  const [decoding, setDecoding] = useState<DecodeTarget>()
  const textArea = useRef<TextAreaRef>(null)
  const messages = messageLines(Form.useWatch('messages', form) ?? '')
  const send = useEngineMutation(
    sessionRef.engineId,
    (body: SendRequest) => call(api.POST('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/messages', { ...sessionPath(sessionRef), body })),
    (body) => (body.messages.length === 1 ? 'Message sent' : `${body.messages.length} messages sent`),
  )
  const where = `${summary.group}.${summary.id}, config ${summary.runningConfigId}`

  return (
    <Form<MessageForm> form={form} layout="vertical" initialValues={{ messages: '', separator: '|', possDup: false }}>
      {!summary.loggedIn && <Alert type="warning" title="The session is not logged in: the engine will refuse the messages." style={{ marginBottom: 16 }} />}
      <Typography.Paragraph type="secondary">
        One message per line, sent in order; the engine stops at the first it refuses, those before it being sent. It
        fills in the header (BeginString, BodyLength, MsgSeqNum, SenderCompID, TargetCompID, SendingTime) and the
        trailer.
      </Typography.Paragraph>
      <Form.Item
        name="messages"
        label="Messages"
        rules={[{ required: true, whitespace: true }]}
        extra={messages.length > 1 ? `${messages.length} messages` : undefined}
      >
        <Input.TextArea
          ref={textArea}
          rows={6}
          placeholder={'35=D|11=ORDER-1|55=AAPL|54=1|38=100|40=1\n35=D|11=ORDER-2|55=MSFT|54=2|38=50|40=1'}
          style={{ fontFamily: 'monospace' }}
        />
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
      <Form.Item name="possDup" valuePropName="checked" extra="Sends them as possible duplicates: each SendingTime(52) becomes OrigSendingTime(122).">
        <Checkbox>PossDup</Checkbox>
      </Form.Item>
      <Flex gap={8}>
        <Button
          title={messages.length > 1 ? 'Decodes the message on the line under the cursor' : undefined}
          onClick={() =>
            form.validateFields(['messages']).then(() =>
              setDecoding({
                ref: { ...sessionRef, sessionId: summary.runningConfigId },
                message: lineAtCursor(form.getFieldValue('messages'), textArea.current?.resizableTextArea?.textArea.selectionStart ?? 0),
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
          confirm={messages.length > 1 ? `Send these ${messages.length} messages on ${where}, in order?` : `Send this message on ${where}?`}
          onConfirm={() => form.validateFields().then((values) => send.mutate({ ...values, messages: messageLines(values.messages) }))}
        >
          Send
        </WriteButton>
      </Flex>
      <DecodedMessageModal target={decoding} onClose={() => setDecoding(undefined)} />
    </Form>
  )
}

function messageLines(text: string): string[] {
  return text.split(/\r?\n/).map((line) => line.trim()).filter((line) => line !== '')
}

function lineAtCursor(text: string, cursor: number): string {
  const start = text.lastIndexOf('\n', cursor - 1) + 1
  const end = text.indexOf('\n', cursor)
  const line = text.substring(start, end === -1 ? text.length : end).trim()
  return line !== '' ? line : messageLines(text)[0]!
}
