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

1. **PostgreSQL com duas contas.** As tabelas usam `FORCE ROW LEVEL SECURITY`, entao a conta da
   aplicacao **nao pode** ser superusuario nem ter `BYPASSRLS` (senao o isolamento por tenant deixa
   de valer) - e por isso migracoes de seed precisam de uma conta propria:

   ```bash
   docker run -d --name milebag-pg -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin -p 5433:5432 postgres:16
   docker exec milebag-pg psql -U admin -d postgres      -c "CREATE ROLE milebag_app LOGIN PASSWORD 'milebag_app' NOSUPERUSER;"      -c "CREATE DATABASE milebag OWNER admin;"
   docker exec milebag-pg psql -U admin -d milebag      -c "GRANT ALL ON SCHEMA public TO milebag_app;"      -c "ALTER DEFAULT PRIVILEGES FOR ROLE admin IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO milebag_app;"      -c "ALTER DEFAULT PRIVILEGES FOR ROLE admin IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO milebag_app;"
   ```

   (Porta 5433 evita conflito com um PostgreSQL local ja instalado na 5432.)

2. **Subir a API** (perfil `dev` e o padrao):

   ```bash
   MILEBAG_DB_URL=jdbc:postgresql://localhost:5433/milebag    MILEBAG_FLYWAY_USER=admin MILEBAG_FLYWAY_PASSWORD=admin    mvn spring-boot:run
   ```

3. **Autenticar:** `node scripts/dev/emitir-tokens.cjs` imprime tokens de motorista, admin do tenant e
   admin da plataforma (nao ha endpoint de login ainda).

4. **Servicos externos** (opcionais para testar): rotas exigem Valhalla (`MILEBAG_VALHALLA_URL`,
   padrao `localhost:8002`); geocodificacao usa o Nominatim publico. SMTP/IMAP e Telegram vem
   desabilitados por padrao (`milebag.smtp/imap/telegram/mensageria.*`).

Os testes com Testcontainers exigem Docker rodando; o `pom.xml` fixa `api.version=1.44` para o
`docker-java` funcionar com Docker Engine 29+.

