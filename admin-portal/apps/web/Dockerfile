# syntax=docker/dockerfile:1
# The portal as static files behind nginx. Build from admin-portal/:
#   docker build -f apps/web/Dockerfile -t qms-admin-web .
# Config is read at container start from VITE_* env vars (see docker/40-qms-env.sh), so the
# same image serves any Firebase project and domain.

FROM node:24-alpine AS build
WORKDIR /workspace
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml tsconfig.base.json ./
COPY packages/contract/package.json packages/contract/
COPY apps/web/package.json apps/web/
RUN pnpm install --frozen-lockfile --filter @qms/web...
COPY packages/contract packages/contract
COPY apps/web apps/web
RUN pnpm --filter @qms/web build

FROM nginxinc/nginx-unprivileged:stable-alpine
COPY apps/web/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chmod=755 apps/web/docker/40-qms-env.sh /docker-entrypoint.d/40-qms-env.sh
COPY --from=build /workspace/apps/web/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
