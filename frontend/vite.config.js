import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
// 前端通过 /api 代理访问后端：默认指向 mock-server(3001)，
// 设置 VITE_API_TARGET=http://localhost:8080 即可切换到 Spring Boot 真实后端
export default defineConfig(function (_a) {
    var mode = _a.mode;
    var env = loadEnv(mode, process.cwd(), '');
    var apiTarget = env.VITE_API_TARGET || 'http://localhost:3001';
    return {
        plugins: [react()],
        resolve: {
            alias: {
                '@': path.resolve(__dirname, 'src')
            }
        },
        server: {
            port: 5173,
            host: true,
            proxy: {
                '/api': {
                    target: apiTarget,
                    changeOrigin: true
                }
            }
        },
        build: {
            outDir: 'dist',
            sourcemap: false
        }
    };
});
