# LeadLens frontend (Angular 22)

Standalone components, signals and zoneless change detection, talking to the Spring Boot API in `../backend`.

```bash
npm ci
npx ng serve     # http://localhost:4200, /api proxied to http://localhost:8090 (proxy.conf.json)
npx ng build     # production build into ../backend/src/main/resources/static (served by Spring Boot)
```

```
src/app/
├── core/       models.ts (API types) · api.service.ts (HttpClient) · lead-store.ts (signal state) · toast.service.ts
├── features/   topbar · dashboard · leads · lead-detail · import · thesis
└── shared/     score-ring · email-badge · format.ts
```

Styling lives in `src/styles.css` (design tokens, light/dark themes).
