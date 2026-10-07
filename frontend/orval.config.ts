import { defineConfig } from 'orval'

export default defineConfig({
  kontra: {
    input: {
      target: '../backend/openapi/openapi.json',
    },
    output: {
      mode: 'tags-split',
      target: 'src/api/generated/endpoints',
      schemas: 'src/api/generated/model',
      client: 'react-query',
      httpClient: 'fetch',
      clean: true,
      mock: false,
      headers: false,
      override: {
        fetch: {
          includeHttpResponseReturnType: false,
        },
        mutator: {
          path: 'src/lib/cliente-api.ts',
          name: 'requisicaoApi',
        },
      },
    },
  },
})
