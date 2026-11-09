
#!/usr/bin/env bash
# Paso 10 - Verificacion practica con cURL
# Ejecutar en una terminal Git Bash (Windows) o bash (Linux/Mac)
# con la aplicacion ya corriendo en http://localhost:8080
URL=http://localhost:8080

echo "===== Prueba 1: Login paciente_carlos y paciente_maria ====="
R_CARLOS=$(curl -s -X POST $URL/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "paciente_carlos", "password": "Password123!"}')
echo "$R_CARLOS"; echo
R_MARIA=$(curl -s -X POST $URL/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "paciente_maria", "password": "Password123!"}')
echo "$R_MARIA"; echo
TOKEN_CARLOS=$(echo "$R_CARLOS" | sed -E 's/.*"token":"([^"]+)".*/\1/')
TOKEN_MARIA=$(echo "$R_MARIA" | sed -E 's/.*"token":"([^"]+)".*/\1/')

echo; echo "===== Prueba 2: Cabeceras defensivas HTTP ====="
curl -s -i -X POST $URL/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "paciente_carlos", "password": "Password123!"}' \
  | sed -n '1,/^\r\?$/p'

echo; echo "===== Prueba 3: Carlos consulta su expediente 101 (200) ====="
curl -s -w "\nHTTP %{http_code}\n" -X GET $URL/api/v1/expedientes/101 \
  -H "Authorization: Bearer $TOKEN_CARLOS"

echo; echo "===== Prueba 4: Maria intenta ver el expediente 101 (403) ====="
curl -s -w "\nHTTP %{http_code}\n" -X GET $URL/api/v1/expedientes/101 \
  -H "Authorization: Bearer $TOKEN_MARIA"

echo; echo "===== Prueba 5: Fuerza bruta contra login (429 desde la 11) ====="
echo "(El limite es global: 10/min. Las 3 llamadas de login de arriba ya"
echo " consumieron fichas; espere 1 minuto antes si quiere ver 10 + 2 exactas)"
for i in {1..12}; do
  curl -s -o /dev/null -w "Peticion $i -> %{http_code}\n" \
    -X POST $URL/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username": "admin_root", "password": "ClaveErronea1"}'
done
