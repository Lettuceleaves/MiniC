import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      "/api": "http://127.0.0.1:7070",
      "/ws": {
        target: "ws://127.0.0.1:7070",
        ws: true,
      },
    },
  },
  test: {
    css: true,
    environment: "jsdom",
    exclude: ["tests/**"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
