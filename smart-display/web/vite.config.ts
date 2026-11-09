import { resolve } from "node:path";
import { defineConfig } from "vite";

const api = "http://localhost:8080";

export default defineConfig({
  server: {
    port: 5173,
    host: true,
    proxy: { "/api": api, "/art": api },
  },
  build: {
    target: "es2022",
    rollupOptions: {
      input: { main: resolve(import.meta.dirname, "index.html"), setup: resolve(import.meta.dirname, "setup.html") },
    },
  },
});
