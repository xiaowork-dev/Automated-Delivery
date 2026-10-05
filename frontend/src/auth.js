import { reactive } from 'vue'
import { api } from './api'

export const session = reactive({ user: null, ready: false })
let initialization
export const clearSession = () => { localStorage.removeItem('delivery_token'); session.user = null; session.ready = true; initialization = undefined }
export const setSession = result => { localStorage.setItem('delivery_token', result.token); session.user = result.user; session.ready = true }
export function initializeSession() {
  if (session.ready) return Promise.resolve(session.user)
  if (initialization) return initialization
  if (!localStorage.getItem('delivery_token')) { session.ready = true; return Promise.resolve(null) }
  initialization = api.get('/auth/me').then(user => { session.user = user; session.ready = true; return user }).catch(error => { if (error.status === 401 || error.status === 403) clearSession(); else { session.ready = false; initialization = undefined; throw error }; return null })
  return initialization
}
