import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite' // 自动导入vue中的组件
import Components from 'unplugin-vue-components/vite' //自动导入ui-组件 比如 element-plus等
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers' // 对应组件库引入 ，AntDesignVueResolver

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    //element-plus按需导入
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [
        // 配置elementPlus采用sass样式配置系统
        ElementPlusResolver({importStyle:"sass"})
      ],
    }),
  ],
  server: {
    port: 4000, // 自定义固定端口（比如 3000，选一个未被占用的即可）
    strictPort: true, // 可选：开启「严格端口模式」，若 3000 被占用直接报错（而非自动换端口）
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/assets/css/index.scss" as *;`,
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
