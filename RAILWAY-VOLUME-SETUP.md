# Configurar Volume no Railway (dados persistentes)

## Por que é necessário?
Sem Volume, os dados (trocas, check-ins, edições) são perdidos a cada redeploy.
Com Volume, os dados ficam permanentes para sempre.

## Como configurar (1 vez apenas)

### Passo 1 - No Railway Dashboard
1. Acesse: https://railway.app/dashboard
2. Clique no seu projeto `escala-medica`
3. Clique no serviço (o container)

### Passo 2 - Adicionar Volume
1. Clique em **"Volumes"** no menu lateral
2. Clique **"Add Volume"**
3. Configure:
   - **Mount Path**: `/app/data`
   - **Size**: 1 GB (suficiente)
4. Clique **"Add"**

### Passo 3 - Redeploy
1. Vá em **Deployments**
2. Clique **"Redeploy"**

## Resultado
Após configurar o Volume:
- ✅ Trocas aprovadas/recusadas ficam salvas para sempre
- ✅ Check-ins da equipe ficam salvos
- ✅ Médicos criados/editados ficam salvos
- ✅ Edições de plantão ficam salvas
- ✅ Redeploys não apagam mais os dados

## Verificar se está funcionando
Acesse: https://web-production-00abb6.up.railway.app/api/stats
Deve mostrar os dados corretos mesmo após redeploy.
