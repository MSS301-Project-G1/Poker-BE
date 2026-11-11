import { readFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import vm from 'node:vm'
import assert from 'node:assert/strict'
import test from 'node:test'

const source = readFileSync(new URL('../../main/resources/swagger-ui/swagger-initializer.js', import.meta.url), 'utf8')

function setup({ storage = new Map(), csrfAvailable = true } = {}) {
  let config
  const requests = []
  const bundle = value => { config = value; return {} }
  bundle.presets = { apis: {} }
  const context = {
    window: { location: { href: 'http://localhost:8080/swagger-ui/index.html', origin: 'http://localhost:8080' } },
    URL, crypto: { randomUUID }, SwaggerUIBundle: bundle, SwaggerUIStandalonePreset: {},
    sessionStorage: { getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value) },
    fetch: async (url, options) => {
      requests.push({ url, options })
      return { ok: csrfAvailable, json: async () => csrfAvailable ? { result: { token: 'fixture-csrf' } } : {} }
    },
  }
  vm.runInNewContext(source, context)
  context.window.onload()
  return { config, requests }
}

test('login and wallet requests share the tab ID and browser cookies; writes obtain the actual CSRF token', async () => {
  const { config, requests } = setup()
  const login = await config.requestInterceptor({ url: '/api/auth/login', method: 'POST', headers: {}, body: '{"email":"fixture@example.test"}' })
  const wallet = await config.requestInterceptor({ url: '/api/wallet/me', method: 'GET', headers: {} })
  assert.equal(login.headers['X-Tab-Id'], wallet.headers['X-Tab-Id'])
  assert.match(login.headers['X-Tab-Id'], /^[0-9a-f-]{36}$/)
  assert.equal(login.headers['X-XSRF-TOKEN'], 'fixture-csrf')
  assert.equal(login.credentials, 'include')
  assert.equal(wallet.credentials, 'include')
  assert.equal(wallet.headers['X-XSRF-TOKEN'], undefined)
  assert.equal(login.body, '{"email":"fixture@example.test"}')
  assert.equal(requests.length, 1)
  assert.equal(requests[0].url, '/api/auth/csrf')
  assert.equal(requests[0].options.credentials, 'include')
})

test('reload reuses the Swagger tab instead of creating a competing tab identity', async () => {
  const storage = new Map()
  const before = setup({ storage })
  const first = await before.config.requestInterceptor({ url: '/api/auth/me', headers: {} })
  const after = setup({ storage })
  const second = await after.config.requestInterceptor({ url: '/api/auth/me', headers: {} })
  assert.equal(first.headers['X-Tab-Id'], second.headers['X-Tab-Id'])
})

test('direct service origins are rejected and documentation reads do not claim a session or request CSRF', async () => {
  const { config, requests } = setup()
  await assert.rejects(config.requestInterceptor({ url: 'http://localhost:8090/api/auth/login', method: 'POST' }), /must use this API gateway/)
  const docs = await config.requestInterceptor({ url: '/v3/api-docs/wallet-service', method: 'GET' })
  assert.equal(docs.credentials, 'include')
  assert.equal(docs.headers, undefined)
  assert.equal(requests.length, 0)
  assert.equal(config.configUrl, '/v3/api-docs/swagger-config')
  assert.equal(config.queryConfigEnabled, false)
})

test('unsafe requests are not sent when CSRF initialization fails', async () => {
  const { config } = setup({ csrfAvailable: false })
  await assert.rejects(config.requestInterceptor({ url: '/api/payments/bundles/fixture/orders', method: 'POST' }), /Unable to get a CSRF token/)
})
