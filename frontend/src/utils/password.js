// Mirrors the server-side policy (PasswordPolicy in the DTOs): 8-128 chars, at least one letter and one digit.
export const PASSWORD_HINT = '8+ characters with at least one letter and one number'

export function validatePassword(password) {
  if (password.length < 8 || password.length > 128) return 'Password must be 8-128 characters.'
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
    return 'Password must contain at least one letter and one number.'
  }
  return ''
}
