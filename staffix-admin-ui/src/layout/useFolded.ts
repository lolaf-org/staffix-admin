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
import { useSearchParams } from 'react-router'

/**
 * Kept in the URL with the filters, so a folded card stays folded across a reload.
 */
export function useFolded(key: string): [boolean, () => void] {
  const [params, setParams] = useSearchParams()
  const folded = params.getAll('folded').includes(key)
  const toggle = () =>
    setParams(
      (current) => {
        if (folded) current.delete('folded', key)
        else current.append('folded', key)
        return current
      },
      { replace: true },
    )
  return [folded, toggle]
}
