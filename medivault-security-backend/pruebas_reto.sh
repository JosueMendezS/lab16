#!/usr/bin/env bash
# Pruebas del reto autonomo (reinicie la app antes de correrlo)
URL=http://localhost:8080/api/v1

tok() {
  curl -s -X POST $URL/auth/login -H "Content-Type: application/json" \
    -H "X-Forwarded-For: $2" \
    -d "{\"username\":\"$1\",\"password\":\"Password123!\"}" \
    | sed -E 's/.*"token":"([^"]+)".*/\1/'
}

echo "===== A1: 6 logins fallidos desde la misma IP (el 6to debe dar 429) ====="
for i in {1..6}; do
  curl -s -o /dev/null -w "Intento $i -> %{http_code}\n" -X POST $URL/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username": "admin_root", "password": "ClaveErronea1"}'
done

echo; echo "===== A2: otra IP (X-Forwarded-For: 10.0.0.9) tiene su propio bucket (401) ====="
curl -s -o /dev/null -w "IP 10.0.0.9 -> %{http_code}\n" -X POST $URL/auth/login \
  -H "Content-Type: application/json" -H "X-Forwarded-For: 10.0.0.9" \
  -d '{"username": "admin_root", "password": "ClaveErronea1"}'

echo; echo "===== B1: Maria intenta ver el expediente 101 (403 IDOR) ====="
TOKEN_MARIA=$(tok paciente_maria 10.0.0.2)
curl -s -w "\nHTTP %{http_code}\n" $URL/expedientes/101 \
  -H "Authorization: Bearer $TOKEN_MARIA" -H "X-Forwarded-For: 10.0.0.2"

echo; echo "===== B2: Tabla AUDIT_LOGS (consultada por admin_root) ====="
sleep 1
TOKEN_ADMIN=$(tok admin_root 10.0.0.3)
curl -s $URL/auditoria -H "Authorization: Bearer $TOKEN_ADMIN" | sed 's/},{/},\n{/g'
echo
