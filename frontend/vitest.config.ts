import { existsSync } from 'node:fs'
import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

/**
 * Coverage thresholds (Document 6, Coverage Targets and Enforcement, "Web app"): 80% statements and branches
 * on src/features/**, 100% on the three files below once they exist (sse.ts on day 6, markers.ts on day 9,
 * cellGrammar.ts on day 6), no threshold on src/shared/ui primitives. A threshold is lowered only by a pull
 * request that changes Document 6.
 */
const fullCoverageFiles = [
  'src/api/sse.ts',
  'src/features/chat/markers.ts',
  'src/features/rules/cellGrammar.ts',
].filter((file) => existsSync(file))

const fullCoverage = { statements: 100, branches: 100, functions: 100, lines: 100 }

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      globals: false,
      setupFiles: ['src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'lcov', 'json-summary'],
        reportsDirectory: 'coverage',
        include: ['src/**/*.{ts,tsx}'],
        exclude: [
          'src/test/**',
          'src/**/*.test.{ts,tsx}',
          'src/main.tsx',
          'src/vite-env.d.ts',
          'src/shared/ui/**',
        ],
        thresholds: {
          'src/features/**': { statements: 80, branches: 80 },
          ...Object.fromEntries(fullCoverageFiles.map((file) => [file, fullCoverage])),
        },
      },
    },
  }),
)
