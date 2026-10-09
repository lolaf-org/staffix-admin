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
import createClient, { type Middleware } from 'openapi-fetch'
import type { components, paths } from './schema'

export type Schemas = components['schemas']

export class ApiError extends Error {
  readonly status: number
  /**
   * Every problem of a refused change, when the backend lists them.
   */
  readonly problems: string[]

  constructor(status: number, message: string, problems: string[] = []) {
    super(message)
    this.status = status
    this.problems = problems
  }
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

/**
 * Metrics or logs of an engine no connector serves: a state to show, not a failure.
 */
export function isNotCollected(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}

export function csrfHeader(): Record<string, string> {
  const token = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
    ?.substring('XSRF-TOKEN='.length)
  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}
}

const csrf: Middleware = {
  onRequest({ request }) {
    if (request.method !== 'GET') {
      Object.entries(csrfHeader()).forEach(([name, value]) => request.headers.set(name, value))
    }
    return request
  },
}

export const api = createClient<paths>()
api.use(csrf)

type FetchResult<T> = Promise<{ data?: T; error?: unknown; response: Response }>

/**
 * Resolves to the response body, or throws an ApiError carrying the problem detail the backend sent.
 */
export async function call<T>(request: FetchResult<T>): Promise<T> {
  const { data, error, response } = await request
  if (!response.ok) {
    throw new ApiError(response.status, problemMessage(error, response), problemList(error))
  }
  return data as T
}

function problemList(error: unknown): string[] {
  const problems = error && typeof error === 'object' ? (error as { problems?: unknown }).problems : undefined
  return Array.isArray(problems) ? problems.map(String) : []
}

function problemMessage(error: unknown, response: Response): string {
  if (error && typeof error === 'object') {
    const problem = error as { detail?: string; title?: string }
    if (problem.detail) return problem.detail
    if (problem.title) return problem.title
  }
  return `${response.status} ${response.statusText}`.trim()
}
