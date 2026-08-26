import { apiClient } from '../../lib/apiClient.js'

/**
 * The profile endpoints.
 *
 * `PUT` replaces rather than patches: a field left out is cleared. The form therefore
 * always sends all three, which is not laziness but the only way to express "I no
 * longer have a target role" through a request that cannot distinguish an omitted
 * field from an emptied one.
 *
 * Note what these functions cannot send. There is no email, no password and no role,
 * because each is a different operation with a different guard, and folding them into
 * one "update profile" call is how an endpoint grows a field that quietly grants
 * somebody administrator rights.
 */

export const EXPERIENCE_LEVELS = ['ENTRY', 'JUNIOR', 'MID', 'SENIOR', 'LEAD']

export async function fetchProfile() {
  const { data } = await apiClient.get('/profile')
  return data
}

export async function updateProfile({ fullName, targetRole, experienceLevel }) {
  const { data } = await apiClient.put('/profile', {
    fullName,
    // Empty strings from an untouched input mean "not set", and the server stores a
    // blank as null anyway — sending null says the same thing in one fewer conversion.
    targetRole: targetRole?.trim() ? targetRole.trim() : null,
    experienceLevel: experienceLevel || null,
  })
  return data
}
