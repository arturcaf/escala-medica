# Escala Medica - Emergencia Ortopedica

App web para gerenciamento de escala medica com modulo de trocas de plantao.

## Deploy no Render

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy)

## Funcionalidades

- Painel com resumo e proximos plantoes
- Calendario mensal interativo  
- Escala completa com busca e filtros
- Modulo de trocas de plantao (solicitar, aprovar, recusar)
- Cadastro da equipe medica
- 365 plantoes (Ago/2026 - Jul/2027)
- 18 medicos cadastrados

## Tecnologia

- Java 17 (sem frameworks externos)
- Servidor HTTP embutido (com.sun.net.httpserver)
- Frontend HTML/CSS/JS puro
- Dados em JSON

## Rodar localmente

```bash
java -jar escala-medica.jar
```

Acesse: http://localhost:8080
