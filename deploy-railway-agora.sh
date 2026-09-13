#!/bin/bash
# Deploy Escala Medica no Railway - GRATUITO
# Execute no Terminal do Mac na pasta do projeto

set -e
echo ""
echo "  ╔══════════════════════════════════════════╗"
echo "  ║   DEPLOY ESCALA MEDICA - Railway.app     ║"
echo "  ║   App online em ~3 minutos, GRATIS       ║"
echo "  ╚══════════════════════════════════════════╝"
echo ""

# 1. Verificar git
if ! command -v git &> /dev/null; then
    echo "ERRO: git nao encontrado. Instale com: xcode-select --install"
    exit 1
fi

# 2. Instalar Railway CLI
echo "  [1/4] Instalando Railway CLI..."
if ! command -v railway &> /dev/null; then
    curl -fsSL https://railway.app/install.sh | sh
    export PATH="$HOME/.railway/bin:$PATH"
fi
echo "  Railway CLI: OK"

# 3. Login Railway
echo ""
echo "  [2/4] Login no Railway..."
echo "  (Abrira o navegador - crie conta gratuita se nao tiver)"
railway login

# 4. Deploy
echo ""
echo "  [3/4] Fazendo deploy..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Init project if needed
railway init --name "escala-medica-hospital" 2>/dev/null || true

# Deploy
railway up --detach
echo "  Deploy enviado!"

# 5. Get URL
echo ""
echo "  [4/4] Obtendo URL..."
sleep 8
URL=$(railway domain 2>/dev/null || echo "")

echo ""
echo "  ╔══════════════════════════════════════════╗"
echo "  ║  DEPLOY CONCLUIDO!                       ║"
if [ -n "$URL" ]; then
echo "  ║  URL: https://$URL"
fi
echo "  ║                                          ║"
echo "  ║  Acesse: railway.app/dashboard           ║"
echo "  ║                                          ║"
echo "  ║  LOGIN ADMIN:                            ║"
echo "  ║  CRM: 27140  |  Senha: 71991402300       ║"
echo "  ╚══════════════════════════════════════════╝"
echo ""
echo "  Compartilhe a URL com sua equipe!"
open "https://railway.app/dashboard" 2>/dev/null || true
