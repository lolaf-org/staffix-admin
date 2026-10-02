import createClient, { type Middleware } from 'openapi-fetch'
import type { components, paths } from './schema'

export type Schemas = components['schemas']

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
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
    throw new ApiError(response.status, problemMessage(error, response))
  }
  return data as T
}

function problemMessage(error: unknown, response: Response): string {
  if (error && typeof error === 'object') {
    const problem = error as { detail?: string; title?: string }
    if (problem.detail) return problem.detail
    if (problem.title) return problem.title
  }
  return `${response.status} ${response.statusText}`.trim()
}
