import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/heebo/400.css'
import '@fontsource/heebo/500.css'
import '@fontsource/heebo/600.css'
import './index.css'
import { App } from './App'

// Server state lives in TanStack Query only (Document 2, Frontend Architecture, key decision 1).
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

const container = document.getElementById('root')
if (!container) {
  throw new Error('index.html has no #root element')
}

createRoot(container).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
