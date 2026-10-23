# Teste local do OAuth2 do Intervals.icu

## Teste automatizado, sem Intervals.icu real

O teste `IntervalsIcuOAuthLocalFlowIntegrationTest` cria, dentro de uma transacao revertida ao final:

- um pagamento ficticio com status `APPROVED`;
- uma geracao com status `COMPLETED`;
- um plano vinculado ao pagamento.

A troca do codigo pelo token e simulada. O teste exercita os repositorios e o servico reais, valida o inicio da autorizacao, o `state`, o callback, o escopo `CALENDAR:WRITE` e o armazenamento cifrado. Tambem comprova que pagamento pendente, geracao em andamento e pagamento sem plano continuam sem acesso.

Execute:

```powershell
.\mvnw.cmd -Dtest=IntervalsIcuOAuthLocalFlowIntegrationTest test
```

O teste usa somente valores ficticios definidos na propria classe e nao faz chamadas ao Intervals.icu nem ao provedor de pagamentos.

## Teste OAuth real com fixture local

Use apenas um banco MySQL local e descartavel. Nao execute os scripts abaixo contra producao ou contra um banco com dados que precisem ser preservados.

1. Cadastre uma aplicacao OAuth no Intervals.icu com o callback local:

   `http://localhost:8080/api/integrations/intervals-icu/callback`

2. Configure as variaveis no processo local, sem adiciona-las a arquivos versionados:

   - `INTERVALS_ICU_CLIENT_ID`
   - `INTERVALS_ICU_CLIENT_SECRET`
   - `INTERVALS_ICU_REDIRECT_URI`
   - `INTERVALS_ICU_TOKEN_ENCRYPTION_KEY` (32 bytes em Base64)

3. Aplique as migrations e inicie a API localmente. Nao habilite log de corpo HTTP ou de parametros sensiveis.

4. No cliente MySQL conectado exclusivamente ao banco local, execute:

   `src/test/resources/fixtures/intervals-icu-oauth-local-setup.sql`

   O script insere dados novos, nao atualiza registros existentes e retorna uma URL local. Essa URL contem o identificador opaco de acesso ao plano ficticio; trate-a como dado sensivel e nao a copie para logs, tickets ou commits.

5. Abra a URL diretamente no navegador, autorize o escopo `CALENDAR:WRITE` no Intervals.icu e aguarde o callback da API. O backend troca o codigo e persiste somente o token cifrado; o token OAuth nao e devolvido ao navegador.

6. Confira o status pelo endpoint do mesmo plano:

   `GET /api/integrations/intervals-icu/plans/{identificador-da-fixture}/status`

7. Ao terminar, execute no mesmo banco local:

   `src/test/resources/fixtures/intervals-icu-oauth-local-cleanup.sql`

O cleanup remove apenas registros marcados com o email reservado `oauth-local-fixture@example.invalid`. Nenhum treino e enviado nesse fluxo.
