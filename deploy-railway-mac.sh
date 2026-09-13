#!/bin/bash
# ================================================
#  ESCALA MEDICA v3 - Deploy Railway (Mac)
#  Execute este script no Terminal do Mac
#  O app ficara online em ~3 minutos, GRATIS
# ================================================

set -e

echo ""
echo "  ╔══════════════════════════════════════════╗"
echo "  ║   ESCALA MEDICA v3 - Deploy Railway      ║"
echo "  ║   App online gratis em ~3 minutos        ║"
echo "  ╚══════════════════════════════════════════╝"
echo ""

# ── PASSO 1: Verificar dependencias ─────────────
echo "  [1/5] Verificando dependencias..."

if ! command -v git &> /dev/null; then
    echo "  ERRO: git nao encontrado."
    echo "  Instale com: xcode-select --install"
    exit 1
fi

if ! command -v node &> /dev/null; then
    echo "  Node.js nao encontrado. Instalando Railway CLI via curl..."
fi

echo "  OK"

# ── PASSO 2: Instalar Railway CLI ────────────────
echo ""
echo "  [2/5] Instalando Railway CLI..."
if ! command -v railway &> /dev/null; then
    curl -fsSL https://railway.app/install.sh | sh
    export PATH="$HOME/.railway/bin:$PATH"
fi
echo "  Railway CLI: OK"

# ── PASSO 3: Login Railway ───────────────────────
echo ""
echo "  [3/5] Login no Railway..."
echo "  (Abrira o navegador para autenticar)"
echo ""
railway login

# ── PASSO 4: Criar projeto e fazer deploy ────────
echo ""
echo "  [4/5] Criando projeto e fazendo deploy..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Initialize railway project if not exists
if [ ! -f ".railway/config.json" ]; then
    railway init --name "escala-medica"
fi

# Deploy
railway up --detach

echo "  Deploy enviado!"

# ── PASSO 5: Obter URL ───────────────────────────
echo ""
echo "  [5/5] Obtendo URL do app..."
sleep 5
railway domain 2>/dev/null || echo "  (URL sera gerada em instantes no dashboard)"

echo ""
echo "  ╔══════════════════════════════════════════╗"
echo "  ║  DEPLOY CONCLUIDO!                       ║"
echo "  ║                                          ║"
echo "  ║  Acesse o dashboard Railway:             ║"
echo "  ║  https://railway.app/dashboard           ║"
echo "  ║                                          ║"
echo "  ║  Sua URL sera algo como:                 ║"
echo "  ║  https://escala-medica.up.railway.app    ║"
echo "  ║                                          ║"
echo "  ║  LOGIN ADMIN:                            ║"
echo "  ║  CRM: 27140  |  Senha: 71991402300       ║"
echo "  ╚══════════════════════════════════════════╝"
echo ""
echo "  Abrindo dashboard Railway..."
open "https://railway.app/dashboard" 2>/dev/null || true