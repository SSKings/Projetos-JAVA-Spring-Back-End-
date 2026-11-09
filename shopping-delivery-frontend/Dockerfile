# ---------- build ----------
# Imagens fixadas por digest (revisão 3, M4): a tag pode ser republicada; o digest não. O Dependabot atualiza.
FROM node:24-alpine@sha256:a34ab6d7f6f4813f4d0c5469d74685604b962fbdbf5793a7bc7b4998ecb867c8 AS build
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
# 1.30 = linha estável atual do nginx (a 1.27 não recebe mais correções)
FROM nginxinc/nginx-unprivileged:1.30-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080 8443
