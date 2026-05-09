import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // cert-service endpoints (port 8082) — must be listed before the catch-all /api rule
      '/api/learner/certificates': 'http://localhost:8082',
      '/api/certificates':         'http://localhost:8082',
      // all other /api traffic → course-service (port 8080)
      '/api': 'http://localhost:8080',
    },
  },
});
