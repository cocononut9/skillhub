import { defineConfig } from '@playwright/test'
import baseConfig from './playwright.label-filters.config'

// 中文 UI 回归：使用独立开发端口；登录态和标签编辑由用例模拟。
export default defineConfig({
  ...baseConfig,
  testMatch: [
    'label-filters.spec.ts',
    'landing-navigation.spec.ts',
    'auth-entry.spec.ts',
    'theme-toggle.spec.ts',
  ],
})
