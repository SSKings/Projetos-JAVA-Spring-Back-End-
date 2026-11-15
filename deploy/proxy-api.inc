proxy_pass http://api:8080;
proxy_http_version 1.1;
proxy_set_header Host $host;
# A API (server.forward-headers-strategy=framework) usa estes cabeçalhos para o IP real e o https.
# $remote_addr (e não $proxy_add_x_forwarded_for): o cliente não consegue forjar o próprio IP.
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto https;
proxy_set_header X-Forwarded-Host $host;
proxy_read_timeout 30s;
# Cabeçalhos de segurança: a borda é a fonte única em produção (security-headers.inc). A API também os envia (vale em
# dev, sem nginx); sem esconder, cada resposta saía com dois HSTS/CSP/X-Frame-Options/... e dois Referrer-Policy com
# valores diferentes (no-referrer × strict-origin-when-cross-origin), deixando a escolha para o navegador.
proxy_hide_header Strict-Transport-Security;
proxy_hide_header Content-Security-Policy;
proxy_hide_header X-Content-Type-Options;
proxy_hide_header X-Frame-Options;
proxy_hide_header Referrer-Policy;
