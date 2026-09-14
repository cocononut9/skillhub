import { describe, expect, it, vi } from 'vitest'

// The i18n config module performs side-effect-only initialization.
// We mock i18next to verify that init is called with expected config.

const initMock = vi.fn().mockReturnThis()
const useMock = vi.fn().mockReturnThis()

vi.mock('i18next', () => ({
  default: {
    use: useMock,
    init: initMock,
  },
}))

vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}))

vi.mock('./locales/zh.json', () => ({
  default: { greeting: '你好' },
}))

// Import triggers the side-effect initialization
await import('./config')

describe('i18n config', () => {
  it('registers react-i18next without browser language detection', () => {
    expect(useMock).toHaveBeenCalledTimes(1)
  })

  it('always initializes in Chinese with a Chinese fallback', () => {
    expect(initMock).toHaveBeenCalledTimes(1)
    const initOptions = initMock.mock.calls[0][0]
    expect(initOptions.lng).toBe('zh')
    expect(initOptions.supportedLngs).toEqual(['zh'])
    expect(initOptions.fallbackLng).toBe('zh')
  })

  it('disables HTML escaping for React interpolation', () => {
    const initOptions = initMock.mock.calls[0][0]
    expect(initOptions.interpolation.escapeValue).toBe(false)
  })

  it('ignores previously stored language preferences', () => {
    const initOptions = initMock.mock.calls[0][0]
    expect(initOptions.detection).toBeUndefined()
  })

  it('loads only the Chinese resource bundle', () => {
    const initOptions = initMock.mock.calls[0][0]
    expect(Object.keys(initOptions.resources)).toEqual(['zh'])
    expect(initOptions.resources.zh).toHaveProperty('translation')
  })
})
