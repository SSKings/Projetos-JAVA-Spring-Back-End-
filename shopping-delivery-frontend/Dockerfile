# ---------- build ----------
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# VITE_* são embutidas no bundle em tempo de build (não coloque segredos aqui)
ARG VITE_API_BASE_URL=/
ARG VITE_COMPANY_LEGAL_NAME
ARG VITE_COMPANY_TRADE_NAME
ARG VITE_COMPANY_CNPJ
ARG VITE_COMPANY_ADDRESS
ARG VITE_COMPANY_EMAIL
ARG VITE_COMPANY_PHONE
ARG VITE_COMPANY_DPO_EMAIL
ARG VITE_TERMS_VERSION
RUN npm run build

# ---------- runtime: nginx sem root ----------
FROM nginxinc/nginx-unprivileged:1.31-alpine
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080 8443
