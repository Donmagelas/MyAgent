# Frontend

## Stack
- Vue 3
- Vite
- Axios

## Run
```bash
npm install
npm run dev
```

The Vite dev server proxies `/api` and `/actuator` to `http://localhost:8080`.

## Integrated backend APIs
- `POST /api/auth/login`
- `POST /api/chat/stream`
- `POST /api/documents/import-file`
- `GET /api/observability/workflows/{workflowId}`

## Notes
- The page creates a session id automatically on load.
- Knowledge file upload is optional and runs before sending the chat request.
- Agent mode can be switched from the composer toolbar.
