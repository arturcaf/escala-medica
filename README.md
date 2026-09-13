# Escala Medica v6 - Hospital Santa Helena

## Arquitetura

```
GitHub (codigo + JAR)
       |
       | push automatico
       v
Railway.app (servidor online 24/7)
       |
       | URL publica
       v
Equipe medica (celular/computador)
```

## Funcionalidades
- Login: Admin (CRM 27140) + Medicos (ID + telefone)
- Tempo Real: SSE - admin edita, todos atualizam instantaneamente
- WhatsApp: CallMeBot - notificacoes automaticas gratuitas
- SQLite: dados persistem no servidor
- Check-in GPS: raio 2km Hospital Santa Helena, Camacari BA
- 365 plantoes (Ago/2026 - Jul/2027), 18 medicos

## Deploy no Railway (gratuito)

```bash
# 1. Instale Railway CLI
curl -fsSL https://railway.app/install.sh | sh

# 2. Login
railway login

# 3. Deploy
railway up
```

## Uso local (Mac)

```bash
curl -L https://github.com/arturcaf/escala-medica/raw/master/atualizar-escala-mac.sh | bash
```

## Credenciais

| Perfil | Login | Senha |
|--------|-------|-------|
| Admin (Dr. Artur) | CRM: 27140 | 71991402300 |
| Alan Ortop | ID: 1 | 71988410202 |
| Rodrigo Antas | ID: 8 | 71988846000 |
| Yuri Serafim | ID: 18 | 71999776475 |

## WhatsApp (CallMeBot - gratuito)
1. Salvar +34 644 59 21 99 no WhatsApp
2. Enviar: I allow callmebot to send me messages
3. Receber apikey e colar na aba WhatsApp do app
