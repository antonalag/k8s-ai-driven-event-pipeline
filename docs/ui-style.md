---
name: Obsidian Metric System
colors:
  surface: '#0e1511'
  surface-dim: '#0e1511'
  surface-bright: '#343b36'
  surface-container-lowest: '#09100c'
  surface-container-low: '#161d19'
  surface-container: '#1a211d'
  surface-container-high: '#242c27'
  surface-container-highest: '#2f3632'
  on-surface: '#dde4dd'
  on-surface-variant: '#bbcabf'
  inverse-surface: '#dde4dd'
  inverse-on-surface: '#2b322d'
  outline: '#86948a'
  outline-variant: '#3c4a42'
  surface-tint: '#4edea3'
  primary: '#4edea3'
  on-primary: '#003824'
  primary-container: '#10b981'
  on-primary-container: '#00422b'
  inverse-primary: '#006c49'
  secondary: '#ffb2b7'
  on-secondary: '#67001b'
  secondary-container: '#b50036'
  on-secondary-container: '#ffc2c4'
  tertiary: '#ffb95f'
  on-tertiary: '#472a00'
  tertiary-container: '#e29100'
  on-tertiary-container: '#523200'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#6ffbbe'
  primary-fixed-dim: '#4edea3'
  on-primary-fixed: '#002113'
  on-primary-fixed-variant: '#005236'
  secondary-fixed: '#ffdadb'
  secondary-fixed-dim: '#ffb2b7'
  on-secondary-fixed: '#40000d'
  on-secondary-fixed-variant: '#92002a'
  tertiary-fixed: '#ffddb8'
  tertiary-fixed-dim: '#ffb95f'
  on-tertiary-fixed: '#2a1700'
  on-tertiary-fixed-variant: '#653e00'
  background: '#0e1511'
  on-background: '#dde4dd'
  surface-variant: '#2f3632'
typography:
  display-metrics:
    fontFamily: JetBrains Mono
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-sm:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  code-sm:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
  label-caps:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 4px
  gutter: 12px
  margin: 16px
  container-padding: 24px
---

## Brand & Style

This design system is engineered for Site Reliability Engineers (SREs) and platform developers who require immediate, low-latency access to system health data. The personality is hyper-functional, technical, and stoic, prioritizing "data over chrome." 

The style is **Minimalist-Technical**. It utilizes a "blacked-out" aesthetic to reduce eye strain during long-term monitoring. By stripping away shadows and gradients, the system ensures that color is used exclusively as a status signal rather than a decorative element. The interface should feel like a high-performance terminal—fast, precise, and devoid of visual noise.

## Colors

The palette is strictly functional, leveraging the Zinc scale for structural elements and high-chroma signals for system status.

- **Primary (Emerald-500):** Indicates operational health, "UP" status, and successful deployments.
- **Secondary (Rose-500):** Reserved for critical failures, breached SLAs, and "DOWN" status.
- **Tertiary (Amber-500):** Indicates latency warnings, pending states, or throttled resources.
- **Accent (Violet-500):** Used exclusively for AI-generated insights, log clusters, and diagnostic summaries.
- **Neutral (Zinc Scale):** Background is Zinc-950 (`#09090b`) to provide maximum contrast for metrics. Surface areas use Zinc-900 (`#18181b`) with Zinc-800 (`#27272a`) borders.

## Typography

The typography system differentiates between **UI Navigation** (Inter) and **Data/Logic** (JetBrains Mono). 

- **Inter** is used for all interface labels, buttons, and navigation to ensure legibility.
- **JetBrains Mono** is the workhorse for metrics, timestamps, log lines, and trace IDs. Its tabular figures ensure that numbers remain vertically aligned in high-density tables and dashboards.
- Use `label-caps` for table headers and section titles to create a clear structural hierarchy without increasing font size.

## Layout & Spacing

This design system employs a **High-Density Fixed Grid** optimized for 1440px+ displays. It uses a 12-column layout with tight 12px gutters to maximize screen real estate.

- **Density:** Information density is paramount. Vertical padding in lists and tables should never exceed 8px.
- **Alignment:** All dashboard widgets must align to a consistent 4px baseline grid. 
- **Responsiveness:** On mobile, widgets stack vertically. On tablet/desktop, use a "Dashboard Masonry" approach where critical health charts span 8 columns and secondary metrics span 4 or 2 columns.

## Elevation & Depth

This design system rejects traditional shadows in favor of **Tonal Layering** and **Flat Borders**.

- **Level 0 (Background):** Zinc-950. Used for the main canvas.
- **Level 1 (Surface):** Zinc-900. Used for widget containers and sidebars.
- **Borders:** Every container uses a 1px solid border of Zinc-800. No rounded corners should overlap without a clear border separator.
- **AI Highlight:** The only exception to the "flat" rule is for AI Insight cards (Violet), which may use a very subtle `0px 0px 15px rgba(139, 92, 246, 0.15)` outer glow to signify their non-deterministic nature.

## Shapes

The shape language is rigid and industrial. 

- **Standard Radius:** 4px (`rounded-sm`) for buttons, input fields, and widget containers. This maintains a sharp, technical look while preventing the interface from feeling "sharp-edged" or aggressive.
- **Status Indicators:** 2px radius for small status pips or "LED" indicators in lists.
- **Strictness:** Do not use pill-shapes (rounded-full) even for tags or buttons, as they consume unnecessary horizontal space and break the grid's visual rigidity.

## Components

- **Metrics Widgets:** Simple Zinc-900 boxes with a 1px border. The value is displayed in `display-metrics` (JetBrains Mono) with a `label-caps` title. Sparklines should be 1px thick with no fill.
- **Buttons:** 
  - *Primary:* Solid Zinc-100 with Zinc-950 text.
  - *Secondary:* Ghost style with Zinc-800 border and Zinc-300 text.
- **Status Chips:** No background fill. Use a 1px border colored by status (Emerald/Rose/Amber) with a matching 4px circular "LED" icon on the left.
- **Log Viewer:** Background Zinc-950, text Zinc-400 using `code-sm`. Keywords (GET, POST, 200, 500) should be syntax-highlighted using the system's status colors.
- **Data Tables:** No zebra striping. Use a 1px Zinc-800 bottom border for rows. Hover states should use a subtle Zinc-800 background.
- **Inputs:** Darker than the surface (Zinc-950) with a 1px Zinc-800 border. Focus state is a 1px Emerald-500 border.