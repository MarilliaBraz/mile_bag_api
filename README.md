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
