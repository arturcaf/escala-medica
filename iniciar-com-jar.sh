#!/bin/bash
# Use este script quando voce ja tem o escala-medica.jar baixado
set -e
JAR_PATH="${1:-./escala-medica.jar}"
if [ ! -f "$JAR_PATH" ]; then
    echo "ERRO: Arquivo $JAR_PATH nao encontrado"
    echo "Uso: bash iniciar-com-jar.sh /caminho/para/escala-medica.jar"
    exit 1
fi
if ! command -v java &> /dev/null; then
    echo "ERRO: Java nao encontrado! Instale em: https://adoptium.net"
    exit 1
fi
mkdir -p ~/escala-medica/data
cp "$JAR_PATH" ~/escala-medica/escala-medica.jar
xattr -d com.apple.quarantine ~/escala-medica/escala-medica.jar 2>/dev/null || true
xattr -cr ~/escala-medica/escala-medica.jar 2>/dev/null || true
cat > ~/escala-medica/iniciar.sh << 'LAUNCH'
#!/bin/bash
cd ~/escala-medica
open http://localhost:8080 2>/dev/null &
java -jar escala-medica.jar
LAUNCH
chmod +x ~/escala-medica/iniciar.sh
echo ""
echo "======================================"
echo "  ESCALA MEDICA v4 - SQLite Database"
echo "  Iniciando servidor..."
echo "  Acesse: http://localhost:8080"
echo ""
echo "  LOGIN ADMIN: CRM 27140 | Senha: 71991402300"
echo "  LOGIN MEDICOS: ID (1-18) + telefone"
echo "  BANCO: ~/escala-medica/data/escala.db"
echo "======================================"
echo ""
(sleep 2 && open http://localhost:8080) &
cd ~/escala-medica
java -jar escala-medica.jar
