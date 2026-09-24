# mile_bag_api

Back-end do **Milebag — Gestão de Última Milha de Bagagens**, aplicação SaaS multi-tenant para digitalizar a devolução de bagagens extraviadas no transporte aéreo brasileiro, da liberação pela companhia aérea (BDO) até a entrega comprovada ao passageiro e o fechamento financeiro do motorista.

## Stack

- **Linguagem:** Java 21 (LTS)
- **Framework:** Spring Boot 3 (Spring MVC)
- **Persistência:** Spring Data JPA com Hibernate
- **Banco de dados:** PostgreSQL com PostGIS e Row Level Security (isolamento entre tenants)
- **Versionamento de banco:** Flyway
- **Segurança:** Spring Security com JWT (claim de tenant)
- **Build:** Maven
- **OCR:** Tesseract OCR 5 (via Tess4J) — extração de dados do BDO
- **Rotas e pedágios:** Valhalla ou OSRM sobre dados do OpenStreetMap
- **Relatórios:** Apache PDFBox ou JasperReports Library
- **Testes:** JUnit 5, Mockito, Testcontainers
- **Documentação da API:** OpenAPI (Springdoc)

## Arquitetura

Multi-tenant por **tabelas compartilhadas**: uma única base de dados, com coluna discriminadora de tenant e isolamento imposto por Row Level Security, ativado a partir da identidade do tenant contida no token JWT.

Organização por camadas `core` (infraestrutura genérica, sem regra de negócio) e `business` (regras de negócio por domínio) — ver `CONVENTIONS.md` no repositório [`mile_bag_audite`](https://github.com/MarilliaBraz/mile_bag_audite) para o detalhamento das convenções técnicas e de código.

## Repositórios relacionados

- [`mile_bag_documentacao`](https://github.com/MarilliaBraz/mile_bag_documentacao) — documentação do projeto (TAP, escopo, sessões)
- [`mile_bag_app`](https://github.com/MarilliaBraz/mile_bag_app) — front-end / aplicação do motorista
- [`mile_bag_infra`](https://github.com/MarilliaBraz/mile_bag_infra) — infraestrutura e implantação
- [`mile_bag_audite`](https://github.com/MarilliaBraz/mile_bag_audite) — constituição e convenções técnicas do projeto

## Rodando localmente

Pre-requisitos: JDK 21, Maven, Docker. (Sem Maven instalado, use o binario do Apache Maven.)

1. **Dependencias externas** (PostgreSQL com duas contas, Valhalla, Postfix+Dovecot): use o
   `docker-compose.yml` do repositorio `mile_bag_infra` — o passo a passo (certificado do e-mail,
   contas, mapa do Valhalla, truststore) esta no README de la. O minimo para a API subir e:

   ```bash
   cd ../mile_bag_infra && docker compose up -d postgres
   ```

   As tabelas usam `FORCE ROW LEVEL SECURITY`, entao a conta da aplicacao (`milebag_app`) **nao pode**
   ser superusuario nem ter `BYPASSRLS`; as migracoes (seed) rodam com a conta `admin` (Flyway).

2. **Subir a API** (perfil `dev` e o padrao):

   ```bash
   MILEBAG_DB_URL=jdbc:postgresql://localhost:5433/milebag MILEBAG_FLYWAY_USER=admin MILEBAG_FLYWAY_PASSWORD=admin      mvn spring-boot:run
   ```

   Para ligar rotas, e-mail e Telegram, veja as variaveis `MILEBAG_VALHALLA_URL`, `MILEBAG_SMTP_*`,
   `MILEBAG_MENSAGERIA_IMAP_*` e `MILEBAG_TELEGRAM_*` no README do `mile_bag_infra`. Com o servidor de
   e-mail local (certificado de uma CA de desenvolvimento) a JVM precisa do truststore gerado la:
   `-Dspring-boot.run.jvmArguments="-Djavax.net.ssl.trustStore=<mile_bag_infra>/data/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"`.

3. **Autenticar:** `POST /api/v1/auth/login` (HU22). No perfil `dev` a API cria contas padrao, todas com
   senha `12345678`: `admin` (administrador da plataforma), `empresa` (administrador da empresa `dev`) e
   `motorista`. **So para desenvolvimento** — sobem apenas com `milebag.dev.seed-usuarios=true` e o segredo
   de JWT de desenvolvimento. Para scripts, faca o login pela API e use o `token` devolvido (o antigo `scripts/dev/emitir-tokens.cjs` foi removido: seus tokens eram de usuarios inexistentes e a API agora recusa sessao de conta que nao esta no banco).

4. **Servicos externos** desabilitados por padrao (`milebag.smtp/imap/telegram/mensageria.*`); a
   geocodificacao usa o Nominatim publico. O bot do Telegram real nao esta provisionado (exige uma
   conta no `@BotFather`); `milebag.telegram.api-base-url` permite apontar para o servidor falso
   `mile_bag_infra/scripts/fake-telegram.cjs`.

Os testes com Testcontainers exigem Docker rodando; o `pom.xml` fixa `api.version=1.44` para o
`docker-java` funcionar com Docker Engine 29+.

