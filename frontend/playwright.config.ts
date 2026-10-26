import {defineConfig} from '@playwright/test'
export default defineConfig({testDir:'./tests',testMatch:'*.spec.ts',workers:1,retries:0,reporter:'list',timeout:30000,use:{baseURL:process.env.PLAYWRIGHT_BASE_URL??'http://127.0.0.1:5178',channel:'msedge',headless:true,viewport:{width:1440,height:1040},screenshot:'only-on-failure'},outputDir:'test-results'})
