/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the PolicyPilot API; baked in at build time (Document 2, Deployment Topology). */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
