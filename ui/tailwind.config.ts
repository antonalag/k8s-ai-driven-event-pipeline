import type { Config } from 'tailwindcss';

/**
 * Obsidian Metric System — Tailwind Configuration
 *
 * Design tokens derived from the Obsidian Metric System spec.
 * Palette: Zinc scale for structure, high-chroma signals for status.
 * Typography: Inter (UI), JetBrains Mono (data/metrics).
 * Shapes: 4px standard radius, no pill shapes, no rounded-full on containers.
 */
const config: Config = {
  prefix: 'kd-',
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // ── Zinc Structural Scale ────────────────────────────────────────
        surface: '#09090b',           // Zinc-950 — main canvas (Level 0)
        'surface-dim': '#09090b',
        'surface-bright': '#3f3f46',  // Zinc-700
        'surface-container-lowest': '#09090b',  // Zinc-950
        'surface-container-low': '#18181b',     // Zinc-900 (Level 1)
        'surface-container': '#18181b',         // Zinc-900
        'surface-container-high': '#27272a',    // Zinc-800
        'surface-container-highest': '#3f3f46', // Zinc-700
        'on-surface': '#fafafa',      // Zinc-50
        'on-surface-variant': '#a1a1aa', // Zinc-400
        'inverse-surface': '#fafafa',
        'inverse-on-surface': '#18181b',
        outline: '#52525b',           // Zinc-600
        'outline-variant': '#27272a', // Zinc-800 — container borders
        background: '#09090b',        // Zinc-950
        'on-background': '#fafafa',

        // ── Status Signals (high-chroma) ─────────────────────────────────
        primary: '#10b981',           // Emerald-500 — operational health
        'on-primary': '#022c22',
        'primary-container': '#059669', // Emerald-600
        'on-primary-container': '#d1fae5',
        'inverse-primary': '#047857',
        'primary-fixed': '#6ee7b7',     // Emerald-300
        'primary-fixed-dim': '#10b981', // Emerald-500
        'on-primary-fixed': '#022c22',
        'on-primary-fixed-variant': '#047857',

        secondary: '#f43f5e',         // Rose-500 — critical failures
        'on-secondary': '#fff1f2',
        'secondary-container': '#e11d48', // Rose-600
        'on-secondary-container': '#ffe4e6',
        'secondary-fixed': '#fda4af',   // Rose-300
        'secondary-fixed-dim': '#f43f5e',
        'on-secondary-fixed': '#4c0519',
        'on-secondary-fixed-variant': '#be123c',

        tertiary: '#f59e0b',          // Amber-500 — warnings/pending
        'on-tertiary': '#451a03',
        'tertiary-container': '#d97706', // Amber-600
        'on-tertiary-container': '#fef3c7',
        'tertiary-fixed': '#fcd34d',    // Amber-300
        'tertiary-fixed-dim': '#f59e0b',
        'on-tertiary-fixed': '#451a03',
        'on-tertiary-fixed-variant': '#b45309',

        error: '#ef4444',             // Red-500
        'on-error': '#fef2f2',
        'error-container': '#dc2626', // Red-600
        'on-error-container': '#fee2e2',

        // ── AI Accent (Violet) ───────────────────────────────────────────
        'ai-violet': '#8b5cf6',       // Violet-500
        'ai-violet-dim': '#7c3aed',   // Violet-600
        'ai-violet-glow': 'rgba(139, 92, 246, 0.15)',

        'surface-tint': '#10b981',
        'surface-variant': '#27272a',
      },

      // ── Typography ──────────────────────────────────────────────────────
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'monospace'],
        'display-metrics': ['JetBrains Mono', 'monospace'],
        'headline-sm': ['Inter', 'sans-serif'],
        'body-md': ['Inter', 'sans-serif'],
        'code-sm': ['JetBrains Mono', 'monospace'],
        'label-caps': ['Inter', 'sans-serif'],
      },
      fontSize: {
        'display-metrics': ['32px', { lineHeight: '40px', letterSpacing: '-0.02em', fontWeight: '700' }],
        'headline-sm': ['16px', { lineHeight: '24px', fontWeight: '600' }],
        'body-md': ['14px', { lineHeight: '20px', fontWeight: '400' }],
        'code-sm': ['12px', { lineHeight: '18px', fontWeight: '400' }],
        'label-caps': ['11px', { lineHeight: '16px', letterSpacing: '0.05em', fontWeight: '700' }],
      },

      // ── Spacing (4px unit, 12px gutter) ─────────────────────────────────
      spacing: {
        unit: '4px',
        gutter: '12px',
        margin: '16px',
        'container-padding': '24px',
      },

      // ── Border Radius (rigid, industrial) ──────────────────────────────
      borderRadius: {
        none: '0',
        sm: '2px',        // Status pips / LED indicators
        DEFAULT: '4px',   // Standard: buttons, inputs, widgets
        md: '4px',        // Same as default — no escalation
        lg: '4px',        // Override Tailwind lg to stay rigid
        xl: '4px',        // Override Tailwind xl
        '2xl': '4px',
        full: '9999px',   // Only for circular status dots
      },

      // ── Animations ──────────────────────────────────────────────────────
      keyframes: {
        'pulse-soft': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.5' },
        },
        reveal: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        shimmer: {
          '0%': { transform: 'translateX(-100%)' },
          '100%': { transform: 'translateX(100%)' },
        },
      },
      animation: {
        'pulse-soft': 'pulse-soft 2s ease-in-out infinite',
        reveal: 'reveal 0.4s ease-out forwards',
        shimmer: 'shimmer 2s linear infinite',
      },
    },
  },
  plugins: [],
};

export default config;
