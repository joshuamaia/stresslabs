# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /app
# CAs extras opcionais (ex.: inspeção TLS de antivírus/proxy) - ver certs/README.md
COPY --from=certs . /tmp/certs/
RUN cat /tmp/certs/*.crt > /tmp/extra-ca.pem 2>/dev/null; true
ENV NODE_EXTRA_CA_CERTS=/tmp/extra-ca.pem
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/stresslabsweb/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/healthz || exit 1
