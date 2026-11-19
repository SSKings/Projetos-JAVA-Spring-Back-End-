# ---------- build ----------
# Imagens fixadas por digest (revisão 3, M4): a tag pode ser republicada; o digest não. O Dependabot atualiza.
FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS build
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
# Correções de segurança do Alpine que a imagem oficial ainda não trouxe (o scan do CI barra HIGH/CRITICAL corrigíveis);
# curl não é usado pelo nginx nem pelos healthchecks: sai da imagem (menos superfície). Depois, de volta ao usuário sem root.
USER root
RUN apk upgrade --no-cache && apk del --no-cache curl
USER 101
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080 8443
