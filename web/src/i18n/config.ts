import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zh from './locales/zh.json'

/**
 * 当前站点仅使用中文。忽略浏览器语言及此前保存的语言偏好，
 * 避免老用户刷新后仍进入英文或俄文界面。
 */
i18n
  .use(initReactI18next)
  .init({
    resources: {
      zh: { translation: zh },
    },
    lng: 'zh',
    supportedLngs: ['zh'],
    fallbackLng: 'zh',
    interpolation: {
      escapeValue: false,
    },
  })

export default i18n
