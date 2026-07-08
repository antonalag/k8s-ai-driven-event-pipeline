# ui/

Observability Dashboard — Real-time Kubernetes failure diagnosis and 1-click remediation interface.

---

## Tech Stack

| Component | Version |
|-----------|---------|
| React | 18.x |
| TypeScript | 5.x (strict mode, `noImplicitAny: true`) |
| Vite | 6.x |
| TanStack Query (React Query) | 5.x |
| Tailwind CSS | 3.x |
| Shadcn/ui | Latest primitives |
| Vitest | 2.x |
| fast-check | 3.x (Property-Based Testing) |
| Testing Library | React + Jest-DOM |
| Nginx | 1.27 Alpine (production serving) |

---

## Responsibilities

1. **Real-Time Polling** — TanStack Query fetches `GET /api/v1/analyses` at a 5-second interval, keeping the dashboard synchronized with the backend.
2. **Analysis Rendering** — Displays AI analysis cards with verdict badge (color-coded LED indicator), root cause, and recommended actions. Cards animate in on first appearance.
3. **1-Click Remediation** — `ExecuteActionButton` triggers `POST /api/v1/remediations` via TanStack Mutation hooks. Only visible for parseable mutation commands (`set image`, `rollout restart`, `scale`). Diagnostic commands show only Copy.
4. **RFC 7807 Error Handling** — Parses `ProblemDetail` responses from the backend and renders structured error banners.
5. **Automatic Card Dismissal** — After successful remediation, the card shows a success banner (2s), then fades out with exit animation (1.5s). Backend filtering (`latestPerPod` with HEALTHY exclusion) ensures recovered pods don't reappear on subsequent polls.
6. **DEGRADED Filtering** — Analyses with `verdict=DEGRADED` (circuit breaker fallback) are hidden from the UI.
7. **Action Parsing** — `action-parser.ts` detects which recommended actions are executable mutations and controls Execute button visibility.

---

## Component Architecture

```
src/
├── components/
│   ├── Sidebar.tsx              # Navigation panel (cluster header + Dashboard)
│   ├── TopBar.tsx               # Breadcrumbs + live connection indicator
│   ├── AnalysisCard.tsx         # Individual diagnosis card (verdict, root cause, actions)
│   ├── EmptyState.tsx           # Zero-results placeholder
│   ├── RecommendedActionBlock.tsx  # Action display + Copy + Execute buttons
│   ├── ExecuteActionButton.tsx  # 1-click remediation trigger with mutation hook
│   └── RemediationBanner.tsx    # Success/error banner after execution
├── hooks/
│   └── useRemediation.ts        # TanStack Mutation hook for POST /api/v1/remediations
├── api/
│   ├── client.ts                # HTTP client + fetchAnalyses + RFC 7807 parsing
│   ├── hooks.ts                 # useAnalyses — TanStack Query polling hook
│   └── mappers.ts              # DTO → display model mapping utilities
├── lib/
│   └── action-parser.ts         # Parses kubectl commands into typed remediation requests
├── types/
│   ├── api.ts                   # AiAnalysisResponse, ProblemDetail interfaces
│   ├── dashboard.ts             # Navigation, style, and component prop types
│   └── remediation.ts           # RemediationRequest, ParsedAction types
├── App.tsx                      # Root shell — sidebar + topbar + analysis card list
└── main.tsx                     # Vite entry point
```

---

## TanStack Query Integration

### Polling (Read Path)

```typescript
useQuery({
  queryKey: ['analyses', { namespace, podName }],
  queryFn: () => fetchAnalyses({ namespace, podName }),
  refetchInterval: 5000,  // 5-second polling
});
```

### Mutation (Write Path)

```typescript
useMutation({
  mutationFn: (request: RemediationRequest) =>
    postRemediation(request),
  onSuccess: () => queryClient.invalidateQueries(['analyses']),
  onError: (error) => parseRfc7807(error),
});
```

The mutation hook provides:
- **Optimistic UI lock** — Disables the execute button during in-flight requests
- **Error mapping** — Parses RFC 7807 `ProblemDetail` into rendered error banners
- **Cache invalidation** — Triggers re-fetch of analyses on success to reflect healed state

---

## Testing Strategy

### Quality Gates

```bash
# All gates must pass (enforced in CI)
npm run typecheck   # tsc --noEmit (strict mode)
npm run lint        # ESLint compliance
npm run test        # Vitest + fast-check
npm run build       # Vite production build
```

### Property-Based Testing (PBT) with fast-check

| Property | Invariant Validated |
|----------|-------------------|
| Navigation Exclusivity | For any `NavItemId`, exactly one item is active after click |
| Log Entry Structural Completeness | Every valid `LogEntry` renders timestamp, severity, and message |
| Severity Visual Treatment | Color class matches severity level; highlight applied only for ERROR/CRIT |
| ProblemDetail Field Rendering | All four ProblemDetail fields are visible in the rendered output |

PBT generates hundreds of randomized inputs per property, catching edge cases that example-based tests miss.

### Running Tests

```bash
# Run all tests (single execution, no watch)
npm run test

# Run in watch mode (development)
npx vitest

# Run a specific test file
npx vitest navigationExclusivity
```

---

## Development

```bash
# Install dependencies
npm ci

# Start dev server (hot reload)
npm run dev

# Production build
npm run build

# Preview production build locally
npm run preview
```

### Environment

The dev server proxies `/api/` requests to `http://localhost:8082` (ai-analyzer). In containerized mode, Nginx handles this proxy internally.

---

## Container

```dockerfile
# Stage 1: Node 20 Alpine — npm ci + vite build
# Stage 2: Nginx 1.27 Alpine — serve static assets
# Non-root, read-only filesystem, SPA routing via try_files
```

Exposed port: **80** (mapped to host **3000** in Docker Compose)
Health endpoint: `GET /healthz` (Nginx static 200)

### Nginx Configuration

- SPA routing: `try_files $uri $uri/ /index.html`
- API proxy: `/api/` → `http://ai-analyzer:8082/api/` (zero-CORS in container mode)
- Security headers: `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`
- Gzip compression enabled for static assets
