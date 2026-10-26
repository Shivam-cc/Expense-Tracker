import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Kept separate from vite.config.js so tests don't load the Tailwind and PWA plugins.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    include: ['src/**/*.test.{js,jsx}'],
    restoreMocks: true,
    unstubGlobals: true,
  },
})
