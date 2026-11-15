import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'

// jsdom has no ResizeObserver; Radix primitives (e.g. Checkbox) use it to measure themselves.
if (!('ResizeObserver' in globalThis)) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})
