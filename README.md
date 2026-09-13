# Escala Medica v4 - Hospital Santa Helena

App web completo para gerenciamento de escala medica com:
- Login por CRM/ID + telefone
- Check-in/Check-out por GPS (raio 2km - Hospital Santa Helena, Camacari BA)
- Notificacoes em tempo real
- Banco de dados SQLite (dados persistem apos reinicializacao)
- Painel Admin exclusivo (Dr. Artur Cesar - CRM 27140)
- 365 plantoes (Ago/2026 - Jul/2027)
- 18 medicos cadastrados

## Como usar no Mac

### 1. Instale o Java
https://adoptium.net

### 2. Clone este repositorio
```bash
git clone https://github.com/arturcaf/escala-medica
cd escala-medica
bash iniciar-com-jar.sh escala-medica.jar
```

### 3. Acesse
http://localhost:8080

## Credenciais

| Perfil | Login | Senha |
|--------|-------|-------|
| Admin (Dr. Artur) | CRM: 27140 | 71991402300 |
| Alan Ortop | ID: 1 | 71988410202 |
| Rodrigo Antas | ID: 8 | 71988846000 |
| Yuri Serafim | ID: 18 | 71999776475 |

Senha = telefone sem formatacao (apenas numeros)

## Deploy online (Railway)
```bash
bash deploy-railway-mac.sh
```

## Tecnologia
- Java 17 (sem frameworks externos)
- SQLite via JDBC (persistencia real)
- Frontend HTML/CSS/JS puro
- GPS via Geolocation API
