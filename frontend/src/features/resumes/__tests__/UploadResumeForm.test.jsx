import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import UploadResumeForm from '../UploadResumeForm.jsx'
import { uploadResume } from '../resumeApi.js'

/**
 * The client-side limits, which exist to save a five-megabyte round trip and turn a 413
 * into a sentence. The server remains the authority — these tests assert the form asks
 * first, and never that it decides.
 */
vi.mock('../resumeApi.js', () => ({
  uploadResume: vi.fn(),
  listResumes: vi.fn(),
  getResume: vi.fn(),
  deleteResume: vi.fn(),
}))

function fileOf(name, type, size) {
  const file = new File(['resume text'], name, { type })
  if (size !== undefined) Object.defineProperty(file, 'size', { value: size })
  return file
}

function fileInput(container) {
  return container.querySelector('input[type="file"]')
}

describe('UploadResumeForm', () => {
  beforeEach(() => vi.clearAllMocks())

  it('refuses a file type the extractor cannot read, before uploading it', async () => {
    const { container } = render(<UploadResumeForm />)

    // `applyAccept: false` bypasses the file dialog's own filter, which is exactly the
    // case this check exists for: a dropped file, or one renamed past the extension.
    await userEvent.upload(fileInput(container), fileOf('notes.txt', 'text/plain'), {
      applyAccept: false,
    })

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That file type is not supported. Upload a PDF or a DOCX.',
    )
    expect(screen.getByRole('button', { name: /Upload resume/ })).toBeDisabled()
    expect(uploadResume).not.toHaveBeenCalled()
  })

  it('says how big the file is when it is over the limit', async () => {
    const { container } = render(<UploadResumeForm />)

    await userEvent.upload(fileInput(container), fileOf('huge.pdf', 'application/pdf', 6 * 1024 * 1024))

    expect(await screen.findByRole('alert')).toHaveTextContent('That file is 6.0 MB. The limit is 5 MB.')
    expect(uploadResume).not.toHaveBeenCalled()
  })

  it('derives a label from the filename and hands the upload back to its caller', async () => {
    const uploaded = { id: 'r1', label: 'rohit-backend', analysable: true }
    uploadResume.mockResolvedValue(uploaded)
    const onUploaded = vi.fn()

    const { container } = render(<UploadResumeForm onUploaded={onUploaded} />)

    await userEvent.upload(fileInput(container), fileOf('rohit-backend.pdf', 'application/pdf', 90_000))

    // The extension is stripped, because "rohit-backend.pdf" is a filename and
    // "rohit-backend" is a label.
    expect(screen.getByLabelText('Label')).toHaveValue('rohit-backend')

    await userEvent.click(screen.getByRole('button', { name: /Upload resume/ }))

    expect(uploadResume).toHaveBeenCalledTimes(1)
    expect(uploadResume.mock.calls[0][0]).toMatchObject({ label: 'rohit-backend' })
    expect(onUploaded).toHaveBeenCalledWith(uploaded)
  })

  it('shows the reason the server gave when the upload is rejected', async () => {
    const failure = new Error('We could not read any text from this file.')
    uploadResume.mockRejectedValue(failure)

    const { container } = render(<UploadResumeForm />)

    await userEvent.upload(fileInput(container), fileOf('scan.pdf', 'application/pdf', 90_000))
    await userEvent.click(screen.getByRole('button', { name: /Upload resume/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'We could not read any text from this file.',
    )
  })
})
