# Build context is the LEVEL 3 REPO ROOT (see docker-compose "nginx" service).
# Stage 1 builds the Vue SPA (same-origin /api); stage 2 serves it via Nginx.
FROM node:18-alpine AS frontend-build

WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
ENV VITE_API_URL=""
RUN npm run build

FROM nginx:alpine

COPY nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /build/dist /usr/share/nginx/html

EXPOSE 80
