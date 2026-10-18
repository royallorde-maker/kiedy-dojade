import { defineConfig } from 'vite';

// BASE_PATH is set by the GitHub Pages workflow (e.g. /kiedy-dojade/).
export default defineConfig({
  base: process.env.BASE_PATH ?? '/',
  build: {
    target: 'es2022',
    sourcemap: false,
  },
});
