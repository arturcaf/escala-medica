#!/bin/bash
# Atualiza o Escala Medica para a versao mais recente
set -e

echo ""
echo "  Atualizando Escala Medica v5..."
echo "  Login + Admin + Edicoes em Tempo Real"
echo ""

# Verificar Java
if ! command -v java &> /dev/null; then
    echo "ERRO: Java nao encontrado! Instale em: https://adoptium.net"
    exit 1
fi

# Parar servidor atual
pkill -f escala-medica.jar 2>/dev/null || true
sleep 1

# Criar pasta
mkdir -p ~/escala-medica/data
cd ~/escala-medica

# Baixar JAR atualizado do GitHub
echo "  Baixando JAR atualizado..."
curl -L --progress-bar \
  "https://github.com/arturcaf/escala-medica/raw/master/escala-medica.jar" \
  -o escala-medica.jar

# Verificar tamanho (deve ser ~13MB)
SIZE=$(wc -c < escala-medica.jar)
if [ "$SIZE" -lt 1000000 ]; then
    echo "ERRO: Download falhou (arquivo muito pequeno: $SIZE bytes)"
    echo "Tente novamente ou verifique sua conexao com a internet"
    exit 1
fi

echo "  JAR baixado: $SIZE bytes"

# Remover quarentena Mac
xattr -d com.apple.quarantine ~/escala-medica/escala-medica.jar 2>/dev/null || true
xattr -cr ~/escala-medica/escala-medica.jar 2>/dev/null || true

echo ""
echo "  ======================================"
echo "  ATUALIZADO COM SUCESSO!"
echo "  Abrindo: http://localhost:8080"
echo ""
echo "  LOGIN ADMIN:"
echo "  CRM: 27140  |  Senha: 71991402300"
echo ""
echo "  LOGIN MEDICOS:"
echo "  ID (1-18) + telefone sem formatacao"
echo "  Ex: ID=1 (Alan Ortop), Senha=71988410202"
echo "  ======================================"
echo ""

# Abrir navegador
(sleep 2 && open http://localhost:8080) &

# Iniciar servidor
java -jar ~/escala-medica/escala-medica.jar
