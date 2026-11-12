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
FROM nginxinc/nginx-unprivileged:1.31-alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080 8443
