import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({plugins:[vue()],server:{proxy:{'/api':{target:process.env.VITE_API_TARGET??'http://127.0.0.1:8088',changeOrigin:false}}},build:{outDir:'dist',sourcemap:false}})
