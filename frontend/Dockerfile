# --- Build of frontend

FROM nginxinc/nginx-unprivileged:1.29-alpine

COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend/index.html frontend/app.js frontend/styles.css /usr/share/nginx/html/

EXPOSE 8080
