window.onload = function () {
  let tabId
  try {
    tabId = sessionStorage.getItem('poker.swagger.tabId')
    if (!tabId) {
      tabId = crypto.randomUUID()
      sessionStorage.setItem('poker.swagger.tabId', tabId)
    }
  } catch { tabId = crypto.randomUUID() }

  window.ui = SwaggerUIBundle({
    configUrl: '/v3/api-docs/swagger-config',
    dom_id: '#swagger-ui',
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: 'StandaloneLayout',
    deepLinking: true,
    validatorUrl: null,
    queryConfigEnabled: false,
    withCredentials: true,
    persistAuthorization: false,
    requestInterceptor: async function (request) {
      const target = new URL(request.url, window.location.href)
      if (target.origin !== window.location.origin) throw new Error('Swagger requests must use this API gateway.')
      request.credentials = 'include'
      if (!target.pathname.startsWith('/api/')) return request
      request.headers = request.headers || {}
      request.headers['X-Tab-Id'] = tabId
      if (!['GET', 'HEAD', 'OPTIONS'].includes((request.method || 'GET').toUpperCase())) {
        const response = await fetch('/api/auth/csrf', { credentials: 'include', cache: 'no-store' })
        const payload = await response.json()
        if (!response.ok || !payload.result?.token) throw new Error('Unable to get a CSRF token. Please try again.')
        request.headers['X-XSRF-TOKEN'] = payload.result.token
      }
      return request
    },
  })
}
