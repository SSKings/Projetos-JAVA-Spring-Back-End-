proxy_pass http://api:8080;
proxy_http_version 1.1;
proxy_set_header Host $host;
# A API (server.forward-headers-strategy=framework) usa estes cabeçalhos para o IP real e o https.
# $remote_addr (e não $proxy_add_x_forwarded_for): o cliente não consegue forjar o próprio IP.
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto https;
proxy_set_header X-Forwarded-Host $host;
proxy_read_timeout 30s;
