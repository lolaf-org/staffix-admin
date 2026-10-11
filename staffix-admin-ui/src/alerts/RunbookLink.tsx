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
import { BookOutlined } from '@ant-design/icons'
import { Typography } from 'antd'

/**
 * Opens in a new tab; the click stays here, so a link inside a clickable row does not open the row too.
 */
export function RunbookLink({ url }: { url: string | null | undefined }) {
  if (!url) return null
  return (
    <Typography.Link href={url} target="_blank" rel="noopener noreferrer" onClick={(event) => event.stopPropagation()}>
      <BookOutlined /> Runbook
    </Typography.Link>
  )
}
