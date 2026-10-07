# syntax=docker/dockerfile:1

# ---- Build: instala dependências e gera os arquivos estáticos ----
FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Arquivos estáticos 644 e diretórios 755 (o nginx só precisa ler).
RUN npm run build \
    && find dist -type d -exec chmod 755 {} + \
    && find dist -type f -exec chmod 644 {} +

# ---- Runtime: nginx sem privilégios servindo a SPA (porta 8080) ----
FROM nginxinc/nginx-unprivileged:1.30-alpine

# Remove o conteúdo padrão da imagem (ex.: 50x.html); precisa de root só neste passo.
USER root
RUN rm -rf /usr/share/nginx/html/*
USER 101

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
