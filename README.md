# Escala Medica v5 - Tempo Real + SQLite

App web completo para gerenciamento de escala medica.

## Funcionalidades
- **Login**: Admin (CRM 27140) + Medicos (ID + telefone)
- **Tempo Real**: Admin edita → todos os usuarios atualizam instantaneamente (SSE)
- **SQLite**: dados persistem apos reinicializacao do servidor
- **Check-in GPS**: raio 2km do Hospital Santa Helena, Camacari BA
- **Notificacoes**: broadcast em tempo real para todos conectados
- **365 plantoes**: Ago/2026 - Jul/2027
- **18 medicos** cadastrados

## Como usar no Mac

```bash
# 1. Instale Java: https://adoptium.net
# 2. Baixe os arquivos:
curl -L https://github.com/arturcaf/escala-medica/raw/master/escala-medica.jar -o escala-medica.jar
curl -L https://github.com/arturcaf/escala-medica/raw/master/iniciar-com-jar.sh -o iniciar-com-jar.sh
# 3. Execute:
bash iniciar-com-jar.sh escala-medica.jar
# 4. Acesse: http://localhost:8080
```

## Credenciais

| Perfil | Login | Senha |
|--------|-------|-------|
| Admin (Dr. Artur) | CRM: 27140 | 71991402300 |
| Alan Ortop | ID: 1 | 71988410202 |
| Rodrigo Antas | ID: 8 | 71988846000 |
| Yuri Serafim | ID: 18 | 71999776475 |

Senha = telefone sem formatacao (apenas numeros)

## Como funciona o Tempo Real (SSE)

Quando o Admin faz qualquer alteracao:
1. Admin edita plantao / aprova troca / recusa troca
2. Servidor envia evento SSE para TODOS os clientes conectados
3. Cada usuario ve a atualizacao instantaneamente sem recarregar

## Banco de dados
- Arquivo: `~/escala-medica/data/escala.db` (SQLite)
- Tabelas: trocas, checkins, notificacoes, escala_overrides
- Dados persistem para sempre, mesmo apos reiniciar
