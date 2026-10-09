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
export const TIME_WINDOWS = [
  { value: 15, label: '15m' },
  { value: 60, label: '1h' },
  { value: 360, label: '6h' },
  { value: 1440, label: '24h' },
  { value: 2880, label: '2d' },
  { value: 10080, label: '7d' },
] as const

/**
 * The window a live view follows: the shortest.
 */
export const LIVE_WINDOW = TIME_WINDOWS[0].value
