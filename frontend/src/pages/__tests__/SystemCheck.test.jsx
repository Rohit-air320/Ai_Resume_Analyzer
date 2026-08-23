import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import SystemCheck from '../SystemCheck.jsx'
import { ApiError, apiClient } from '../../lib/apiClient.js'

vi.mock('../../lib/apiClient.js', async () => {
  const actual = await vi.importActual('../../lib/apiClient.js')
  return {
    ...actual,
    apiClient: { get: vi.fn() },
  }
})

describe('SystemCheck', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reports a healthy API using the values it returned', async () => {
    apiClient.get.mockResolvedValue({
      data: {
        status: 'UP',
        application: 'ResumeIQ',
        version: '0.1.0',
        activeProfiles: ['dev'],
        checkedAt: '2026-08-23T10:00:00Z',
      },
    })

    render(<SystemCheck />)

    expect(await screen.findByText('Connected')).toBeInTheDocument()
    expect(screen.getByText('ResumeIQ')).toBeInTheDocument()
    expect(screen.getByText('0.1.0')).toBeInTheDocument()
    expect(screen.getByText('dev')).toBeInTheDocument()
    expect(apiClient.get).toHaveBeenCalledWith('/health')
  })

  it('shows the error message and how to fix it when the API is unreachable', async () => {
    apiClient.get.mockRejectedValue(
      new ApiError({
        code: 'NETWORK_ERROR',
        message: 'Cannot reach the server. Check your connection and that the API is running.',
      }),
    )

    render(<SystemCheck />)

    expect(await screen.findByText(/Cannot reach the server/)).toBeInTheDocument()
    expect(screen.getByText('mvn spring-boot:run')).toBeInTheDocument()
    expect(screen.getByText(/code: NETWORK_ERROR/)).toBeInTheDocument()
  })
})
