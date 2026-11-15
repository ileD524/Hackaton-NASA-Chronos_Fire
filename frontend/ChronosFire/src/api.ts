const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim().replace(/\/$/, '')

export function apiUrl(path: string): string {
  if (!configuredBaseUrl || !path.startsWith('/')) return path
  return `${configuredBaseUrl}${path}`
}
