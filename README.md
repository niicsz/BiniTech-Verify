# BiniTech Verify

Serviço de códigos de verificação de 6 dígitos para os serviços BiniTech. O cliente escolhe receber o código por **e-mail** ou por **SMS**; o serviço gera o código, envia pelo canal escolhido e confere o que o cliente digitou.

É usado no cadastro do cliente do delivery e no login em aparelho novo (BTPDV-220). Quem chama é sempre outro serviço BiniTech, nunca o navegador.

## Stack

Java 25, Spring Boot 4.1, MongoDB, arquitetura hexagonal e contrato OpenAPI primeiro, no mesmo padrão do PDV e do Product-Import.

```
domain/        Destination, Verification, regras de validade e tentativas
application/   VerificationUseCase e as portas (repositório, envio, gerador, hash)
adapters/
  inbound/web  controller gerado a partir do swagger.yaml
  outbound/    Mongo, SMS (Mobizon), e-mail (Resend), HMAC e gerador de código
config/        beans, segurança por chave de serviço
```

O teste `HexagonalArchitectureTest` compila `domain` e `application` sozinhos e falha se alguma dessas camadas depender de Spring, Mongo ou adaptadores.

## API

Contrato completo em `src/main/resources/openapi/swagger.yaml`; com o serviço no ar, a documentação fica em `/swagger-ui.html`.

Toda chamada em `/api` exige o cabeçalho `X-Verify-Service-Key`.

| Método | Rota | O que faz |
|---|---|---|
| POST | `/api/verifications` | Gera o código e envia por `EMAIL` ou `SMS` |
| GET | `/api/verifications/{id}` | Situação, destino mascarado, tentativas e reenvios restantes |
| POST | `/api/verifications/{id}/resend` | Envia um código novo; o anterior deixa de valer |
| POST | `/api/verifications/{id}/check` | Confere o código; devolve o destino verificado |

Respostas de erro: `400` dados inválidos, `401` sem chave, `404` não encontrada, `409` código já usado, `410` código vencido, `422` código errado (com `remainingAttempts`), `429` limite atingido (com `Retry-After`), `503` canal indisponível.

### Regras

- Código de 6 dígitos gerado com `SecureRandom`, guardado só como HMAC-SHA256 com pepper. A API nunca devolve o código.
- Vale 10 minutos e só pode ser usado uma vez.
- 5 tentativas erradas bloqueiam a verificação.
- Até 3 envios por verificação, com 60 segundos entre eles.
- Até 5 verificações por destino por hora, para conter custo de SMS e abuso.
- Celular só brasileiro com DDD e nono dígito; fixo é recusado porque não recebe SMS.
- O texto do SMS é sem acento para caber em um segmento de 160 caracteres.
- O Mongo apaga as verificações 1 dia depois de vencerem (índice TTL).

## Provedores

| Canal | Provedor | Custo | Observação |
|---|---|---|---|
| E-mail | Resend | Grátis até 3.000 e-mails por mês | Já usado pelo PDV |
| SMS | Mobizon Brasil | Cerca de R$ 0,06 por SMS, pré-pago, sem mensalidade | Paga por Pix, boleto ou cartão |

Para trocar de provedor de SMS basta outro adaptador de `CodeSenderPort`; o resto do serviço não muda. Alternativas avaliadas em 12/11/2026: SMSGo (cerca de US$ 0,048 por SMS, 3 grátis para teste), Unimatrix (US$ 0,0397), AWS SNS (cerca de US$ 0,03) e Twilio (cerca de US$ 0,06). O WhatsApp de autenticação da Meta sai mais barato por mensagem (cerca de US$ 0,007), mas exige modelo aprovado e conta comercial verificada.

Sem chave de um provedor, o canal fica desligado e a API responde `503` para ele. Com `VERIFY_LOG_CODES=true` os canais sem provedor escrevem o código no log; isso é só para desenvolvimento e nunca deve ser ligado em produção.

## Variáveis de ambiente

| Variável | Obrigatória | Padrão | Descrição |
|---|---|---|---|
| `VERIFY_MONGODB_URI` | sim | | URI do MongoDB |
| `VERIFY_MONGODB_DATABASE` | não | `binitech_verify` | Banco |
| `VERIFY_SERVICE_KEY` | sim | | Chave que os outros serviços mandam em `X-Verify-Service-Key` (32+ caracteres) |
| `VERIFY_CODE_PEPPER` | sim | | Segredo do HMAC dos códigos (32+ caracteres). Trocar invalida os códigos pendentes |
| `VERIFY_CODE_TTL` | não | `10m` | Validade do código |
| `VERIFY_MAX_ATTEMPTS` | não | `5` | Tentativas erradas antes de bloquear |
| `VERIFY_MAX_SENDS` | não | `3` | Envios por verificação |
| `VERIFY_RESEND_COOLDOWN` | não | `60s` | Intervalo mínimo entre envios |
| `VERIFY_MAX_PER_DESTINATION_PER_HOUR` | não | `5` | Verificações por destino por hora |
| `VERIFY_LOG_CODES` | não | `false` | Escreve o código no log quando o canal não tem provedor. Só desenvolvimento |
| `MOBIZON_API_KEY` | para SMS | | Chave da API em mobizon.com.br |
| `MOBIZON_SENDER_ID` | não | | Remetente registrado na Mobizon; vazio usa o remetente padrão da conta |
| `MOBIZON_BASE_URL` | não | `https://api.mobizon.com.br` | |
| `RESEND_API_KEY` | para e-mail | | Chave do Resend |
| `RESEND_FROM` | não | `BiniTech <onboarding@resend.dev>` | Remetente verificado no Resend |
| `PORT` | não | `8085` | |

Gerar as chaves: `openssl rand -base64 48`.

## Rodando local

```bash
docker run -d --name verify-mongo -p 27017:27017 mongo:8.0
export VERIFY_MONGODB_URI=mongodb://localhost:27017
export VERIFY_SERVICE_KEY=$(openssl rand -base64 48)
export VERIFY_CODE_PEPPER=$(openssl rand -base64 48)
export VERIFY_LOG_CODES=true
./mvnw spring-boot:run
```

```bash
curl -X POST localhost:8085/api/verifications \
  -H "X-Verify-Service-Key: $VERIFY_SERVICE_KEY" -H 'Content-Type: application/json' \
  -d '{"channel":"SMS","destination":"(11) 98765-4321","purpose":"SIGNUP"}'
```

O código aparece no log; confira com `POST /api/verifications/{id}/check`.

## Testes e CI

`./mvnw verify` roda os testes de domínio, caso de uso, adaptadores (com `MockRestServiceServer` para Mobizon e Resend), controller, filtro de segurança e arquitetura. O CI no GitHub Actions faz build e testes, varredura de dependências com Trivy e build da imagem Docker.

## Deploy

Railway pelo `Dockerfile`, com healthcheck em `/actuator/health` (`railway.json`). Ordem: este serviço primeiro, depois o Auth, que passa a chamá-lo.

Rollback: Redeploy do deploy anterior no Railway. O serviço não altera dados de outros serviços; verificações pendentes durante a volta só precisam ser pedidas de novo.
