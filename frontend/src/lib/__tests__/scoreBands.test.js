import { describe, expect, it } from 'vitest'
import { bandForScore, clampScore, labelForScore, SCORE_BANDS } from '../scoreBands.js'

describe('scoreBands', () => {
  it('covers 0 to 100 with no gaps and no overlaps', () => {
    expect(SCORE_BANDS[0].min).toBe(0)
    expect(SCORE_BANDS.at(-1).max).toBe(100)
    SCORE_BANDS.slice(1).forEach((band, index) => {
      expect(band.min).toBe(SCORE_BANDS[index].max + 1)
    })
  })

  it.each([
    [0, 'critical'],
    [39, 'critical'],
    [40, 'low'],
    [59, 'low'],
    [60, 'moderate'],
    [74, 'moderate'],
    [75, 'strong'],
    [89, 'strong'],
    [90, 'excellent'],
    [100, 'excellent'],
  ])('maps %i to the %s band', (score, expectedId) => {
    expect(bandForScore(score).id).toBe(expectedId)
  })

  it('matches the labels used in the product copy', () => {
    expect(labelForScore(82)).toBe('Strong match')
    expect(labelForScore(12)).toBe('Needs major improvement')
  })

  it('clamps values outside 0-100 and survives junk input', () => {
    expect(clampScore(140)).toBe(100)
    expect(clampScore(-20)).toBe(0)
    expect(clampScore('84')).toBe(84)
    expect(clampScore(84.6)).toBe(85)
    expect(clampScore(undefined)).toBe(0)
    expect(clampScore('not a score')).toBe(0)
    expect(bandForScore(null).id).toBe('critical')
  })
})
