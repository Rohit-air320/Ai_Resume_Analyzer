import { describe, expect, it, vi } from 'vitest'
import { count, formatBytes, formatDate, formatRelative, humanise } from '../format.js'

/**
 * These are the functions every list on every screen passes its values through, which
 * makes their junk handling the interesting part rather than the happy path: a missing
 * timestamp in one row of a history table must render a dash, not take the page down.
 */
describe('format', () => {
  describe('formatBytes', () => {
    it('scales through the units', () => {
      expect(formatBytes(512)).toBe('512 B')
      expect(formatBytes(2048)).toBe('2 KB')
      expect(formatBytes(1024 * 1024 * 2.5)).toBe('2.5 MB')
    })

    it('renders a dash rather than "0 B" for a missing size', () => {
      expect(formatBytes(undefined)).toBe('—')
      expect(formatBytes(0)).toBe('—')
      expect(formatBytes('not a number')).toBe('—')
    })
  })

  describe('count', () => {
    it('pluralises on the number, not on the caller remembering to', () => {
      expect(count(1, 'resume')).toBe('1 resume')
      expect(count(3, 'resume')).toBe('3 resumes')
      expect(count(0, 'resume')).toBe('0 resumes')
    })

    it('takes an irregular plural', () => {
      expect(count(2, 'analysis', 'analyses')).toBe('2 analyses')
    })

    it('treats an absent count as zero', () => {
      expect(count(undefined, 'page')).toBe('0 pages')
    })
  })

  describe('humanise', () => {
    it('turns an API enum into a sentence-cased label', () => {
      expect(humanise('NICE_TO_HAVE')).toBe('Nice to have')
      expect(humanise('HIGH')).toBe('High')
    })

    it('returns an empty string for nothing, so JSX renders nothing', () => {
      expect(humanise(null)).toBe('')
      expect(humanise(undefined)).toBe('')
    })
  })

  describe('formatRelative', () => {
    it('stays relative while that is easier to read', () => {
      vi.useFakeTimers()
      vi.setSystemTime(new Date('2026-08-25T12:00:00Z'))

      expect(formatRelative('2026-08-25T11:59:50Z')).toBe('just now')
      expect(formatRelative('2026-08-25T11:30:00Z')).toBe('30 minutes ago')
      expect(formatRelative('2026-08-25T09:00:00Z')).toBe('3 hours ago')
      expect(formatRelative('2026-08-24T12:00:00Z')).toBe('yesterday')
      expect(formatRelative('2026-08-20T12:00:00Z')).toBe('5 days ago')

      vi.useRealTimers()
    })

    it('falls back to a date once "N days ago" stops helping', () => {
      vi.useFakeTimers()
      vi.setSystemTime(new Date('2026-08-25T12:00:00Z'))

      const old = '2026-05-20T12:00:00Z'
      expect(formatRelative(old)).toBe(formatDate(old))

      vi.useRealTimers()
    })

    it('renders a dash for a missing or unparseable timestamp', () => {
      expect(formatRelative(null)).toBe('—')
      expect(formatRelative('yesterday-ish')).toBe('—')
      expect(formatDate(undefined)).toBe('—')
    })
  })
})
