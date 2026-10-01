# SalonHub

<p align="center">
  <strong>Gestão de salões, barbearias e espaços de estética em um só lugar.</strong><br>
  Agendamentos, equipe, clientes e indicadores para quem atende e para quem administra.
</p>

<p align="center">
  <a href="#-comece-em-poucos-minutos">Começar</a> ·
  <a href="#-o-que-o-sistema-faz">Funcionalidades</a> ·
  <a href="#-arquitetura">Arquitetura</a> ·
  <a href="#-documentação">Documentação</a>
</p>

---

## ✨ O que o sistema faz

O **SalonHub** é uma aplicação web para estabelecimentos de beleza e seus clientes. Cada estabelecimento administra seus próprios dados, enquanto clientes podem descobrir serviços e marcar horários em diferentes locais.

| Para quem | Recursos principais |
| --- | --- |
| **Clientes** | Cadastro, busca de estabelecimentos, consulta de serviços e profissionais, agendamento e gestão do próprio perfil. |
| **Administradores** | Gestão de equipe, serviços e horários; agenda; acompanhamento de atendimentos, relatórios e comissões. |
| **Profissionais** | Agenda própria, perfil, atendimentos realizados e registro de conclusão ou falta. |
| **Administração da plataforma** | Acompanhamento dos estabelecimentos e controle dos seus estados operacionais. |

O fluxo inclui autenticação por JWT, recuperação de senha por e-mail, fotos de profissionais, controle de disponibilidade e isolamento de dados entre estabelecimentos.

## 🚀 Comece em poucos minutos

### Pré-requisitos

- Docker com Compose
- Java 21
- Node.js 22 e npm

Abra três terminais a partir da raiz do repositório:

**1. Infraestrutura** — PostgreSQL, Mailpit e armazenamento de imagens:

```bash
docker compose up -d
```

**2. API** — em `backend`:

```bash
cd backend
./mvnw spring-boot:run
```

No PowerShell, use `.\mvnw.cmd spring-boot:run`.

**3. Interface** — em `frontend`:

```bash
cd frontend
npm ci
npm run dev
```

Abra **[http://localhost:5173](http://localhost:5173)**. Em desenvolvimento, o Vite encaminha `/api/*` para a API em `http://localhost:8080`.

| Serviço | Endereço local |
| --- | --- |
| Aplicação | [localhost:5173](http://localhost:5173) |
| API e Swagger UI | [localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| Caixa de e-mails de desenvolvimento | [localhost:8025](http://localhost:8025) |
| Navegador de arquivos | [localhost:8888](http://localhost:8888) |

> **Ambiente local:** os dados do PostgreSQL e as imagens ficam em volumes Docker. Os valores padrão de acesso são apenas para desenvolvimento. Consulte [Configuração](#-configuração) antes de usar outro ambiente.

### Dados de demonstração

Para explorar as telas com estabelecimentos, profissionais, clientes e agendamentos fictícios, execute uma vez em `backend`:

```bash
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local,seed"
```

O processo cria os dados e encerra sozinho. Depois, inicie a API normalmente. Exemplo de acesso: `admin@studiobella.demo` com senha `Demo@123`. O seed é opcional e foi feito para uso local; a lista de contas e as instruções de recriação estão no [guia detalhado](GUIA_DETALHADO.md#dados-de-demonstração-seed).

## 🧱 Arquitetura

```text
frontend/  React + TypeScript + Vite
    │      interface, rotas e consumo da API
    ▼
backend/   Spring Boot + Spring Security + JPA
    │      regras de negócio, JWT e OpenAPI
    ├── PostgreSQL 16   dados e migrations Flyway
    ├── Mailpit         e-mails no ambiente local
    └── SeaweedFS       imagens via API compatível com S3
```

**Stack:** Java 21, Spring Boot 4.1, PostgreSQL 16, React 19, TypeScript, Vite, Tailwind CSS 4, React Router, TanStack Query, React Hook Form e Zod.

O backend valida o schema do banco e usa migrations Flyway para evoluí-lo. Os arquivos de imagem ficam no armazenamento S3; o PostgreSQL guarda seus metadados. A autenticação usa JWT e as operações administrativas usam o estabelecimento associado ao usuário autenticado.

## ⚙️ Configuração

Os padrões locais permitem iniciar o projeto sem criar um `.env`. Para alterar conexões ou integrar serviços externos, configure as variáveis de ambiente antes de subir a API:

| Variável | Uso | Padrão local |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexão PostgreSQL | `localhost:5433`, `salonhub_user`, `salonhub_pass` |
| `JWT_SECRET` | Assinatura dos tokens | Consulte a configuração do perfil local |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` | Envio de e-mail | Mailpit em `localhost:1025` |
| `FRONTEND_URL` | Links enviados por e-mail | `http://localhost:5173` |
| `STORAGE_TYPE`, `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET` | Armazenamento de imagens | SeaweedFS em `localhost:8333` |
| `SYSTEM_ADMIN_NAME`, `SYSTEM_ADMIN_EMAIL`, `SYSTEM_ADMIN_PASSWORD` | Criação inicial do administrador da plataforma | Não definido |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas pelo backend; no perfil local, `localhost:5173` e `127.0.0.1:5173` já são aceitos | Padrão local |
| `GEOAPIFY_API_KEY` | Opcional: localiza pelo endereço completo antes de consultar as coordenadas do CEP | Não definido |

As opções completas estão em [`application.properties`](backend/src/main/resources/application.properties), nos arquivos de perfil em `backend/src/main/resources` e no [guia detalhado](GUIA_DETALHADO.md#como-executar). O frontend inclui um [exemplo de ambiente](frontend/.env.example); ele é opcional para o fluxo local com proxy do Vite.

Para calcular latitude e longitude automaticamente, preencha CEP, rua, número, cidade e UF e salve o perfil. Sem configuração adicional, o backend usa as coordenadas do CEP fornecidas pela BrasilAPI; esse ponto pode ser aproximado ou não estar disponível. Se `GEOAPIFY_API_KEY` estiver configurada, o backend tenta localizar pelo endereço completo primeiro. As coordenadas ficam nas colunas `establishment_profiles.latitude` e `establishment_profiles.longitude` (migration V28), e uma mudança no endereço recalcula o ponto. Salve novamente o perfil dos estabelecimentos já cadastrados para preencher coordenadas ainda vazias.

Nos detalhes do perfil público, **Abrir no Google Maps** pesquisa o endereço completo cadastrado em outra aba, sem chave de API. O link usa rua, número, bairro, cidade, UF e CEP, em vez das coordenadas aproximadas do CEP. Confira o endereço cadastrado caso o serviço de mapas sugira outro local.

## 🧪 Qualidade e testes

```bash
# Em backend/
./mvnw test

# Em frontend/
npm run lint
npm test
npm run build
```

No Windows, substitua `./mvnw` por `.\mvnw.cmd`. A [CI](.github/workflows/ci.yml) executa os testes do backend com PostgreSQL e valida lint, testes e build do frontend.

## 📚 Documentação

- [Guia detalhado](GUIA_DETALHADO.md) — fluxos, endpoints, autenticação, seed e regras de negócio.
- [Notas de design](frontend/DESIGN_NOTES.md) — decisões visuais da interface.
- [Swagger UI](http://localhost:8080/swagger-ui.html) — referência interativa da API com o backend em execução.

## 📂 Estrutura do repositório

```text
backend/          API Spring Boot, migrations e testes
frontend/         aplicação React e testes
GUIA_DETALHADO.md referência completa dos fluxos e endpoints
.github/workflows/ automação de testes e build
docker-compose.yml serviços locais
```

---

<p align="center">SalonHub · Projeto acadêmico de conclusão de curso</p>
