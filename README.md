# SalonHub

Plataforma web SaaS para gerenciamento de salões de beleza, barbearias e estabelecimentos de estética.

## Tecnologias

- Java 21
- Spring Boot 4.1
- Spring Web MVC
- Spring Data JPA
- Spring Security
- Bean Validation
- Flyway
- PostgreSQL 16
- Docker Compose
- Springdoc OpenAPI / Swagger UI
- React 19 + TypeScript no frontend
- Vite
- Tailwind CSS 4
- React Router
- TanStack Query
- React Hook Form + Zod
- Axios

## O que já existe

### Infraestrutura

- PostgreSQL executado por Docker Compose.
- Banco persistido no volume Docker `postgres_data`.
- Conexão do backend configurável por variáveis de ambiente.
- Flyway responsável pela criação e evolução do banco.
- Hibernate configurado apenas para validar o schema, sem criar tabelas automaticamente.

### Banco de dados

Migration `V1__create_estabelecimentos.sql`:

- Dados cadastrais e de contato do estabelecimento.
- Identificador numérico `BIGINT` gerado automaticamente.
- Endereço.
- CNPJ único.
- Situação ativa ou inativa.
- Datas de criação e atualização.

Migration `V2__create_usuarios.sql`:

- Usuário vinculado obrigatoriamente a um estabelecimento.
- Identificador numérico `BIGINT` gerado automaticamente.
- Perfis `ADMIN` e `PROFISSIONAL`.
- E-mail único.
- Senha armazenada como hash BCrypt.
- Situação ativa ou inativa.

Clientes possuem conta própria, com e-mail único e autenticação JWT. Diferentemente de administradores e profissionais, a conta do cliente não fica vinculada a um único estabelecimento.

Migration `V5__add_mvp_scheduling.sql`:

- Duração obrigatória dos serviços.
- Horário geral de funcionamento do estabelecimento, inicialmente das 09:00 às 18:00.
- Perfil de cliente e vínculo opcional entre usuário e estabelecimento.
- Agendamentos com cliente, profissional, serviço, início, término e situação.

### Primeiro fluxo implementado

O cadastro inicial cria o estabelecimento e seu primeiro administrador em uma única transação. Isso evita que existam contas sem estabelecimento ou estabelecimentos sem responsável.

```text
Estabelecimento
└── Usuário ADMIN
```

Endpoint:

```http
POST /api/auth/cadastro
Content-Type: application/json
```

Exemplo de requisição:

```json
{
  "ownerName": "João da Silva",
  "ownerEmail": "joao@email.com",
  "password": "senha123",
  "establishmentName": "Barbearia do João",
  "cnpj": "12.345.678/0001-90",
  "establishmentEmail": "contato@barbearia.com",
  "establishmentPhone": "(44) 99999-9999"
}
```

Respostas possíveis:

- `201 Created`: estabelecimento e administrador criados.
- `400 Bad Request`: campos inválidos.
- `409 Conflict`: CNPJ ou e-mail já cadastrado.

O CNPJ é salvo apenas com números, o e-mail de acesso é normalizado e a senha nunca é devolvida pela API.

## Como executar

Na raiz do projeto, inicie o PostgreSQL:

```bash
docker compose up -d
```

Conexão padrão:

```text
Host: localhost
Porta: 5433
Banco: salonhub_db
Usuário: salonhub_user
Senha: salonhub_pass
```

Depois, dentro de `backend`, execute a aplicação:

```bash
./mvnw spring-boot:run
```

No Windows também pode ser usado:

```powershell
.\mvnw.cmd spring-boot:run
```

As configurações podem ser substituídas pelas variáveis:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Para criar o administrador da plataforma no primeiro início do backend, configure as três variáveis abaixo. A criação é idempotente: se a conta de administrador do sistema já existir, ela será reutilizada.

- `SYSTEM_ADMIN_NAME`
- `SYSTEM_ADMIN_EMAIL`
- `SYSTEM_ADMIN_PASSWORD` (mínimo de 6 caracteres)

Esse usuário recebe o perfil `SYSTEM_ADMIN`, não fica vinculado a um estabelecimento e acessa a tela de gestão de estabelecimentos após o login.

Por fim, dentro de `frontend`, instale as dependências e execute a aplicação:

```bash
npm install
npm run dev
```

A aplicação sobe em `http://localhost:5173`. Em desenvolvimento, chamadas para `/api/*` são encaminhadas automaticamente para `http://localhost:8080` pelo proxy configurado no Vite, então não é necessário configurar CORS nem variáveis de ambiente para rodar localmente.

## Swagger e OpenAPI

Com o backend executando:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Especificação OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Especificação OpenAPI YAML: <http://localhost:8080/v3/api-docs.yaml>

As rotas do Swagger, cadastro e login são públicas. As demais rotas exigem autenticação.

## Autenticação

Login:

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "joao@email.com",
  "password": "senha123"
}
```

A resposta contém um token JWT do tipo `Bearer`, sua data de expiração e os dados do usuário. Para acessar uma rota protegida, envie:

```http
Authorization: Bearer SEU_TOKEN
```

Consulta da sessão atual:

```http
GET /api/auth/me
Authorization: Bearer SEU_TOKEN
```

O JWT contém o identificador do usuário, o perfil, o e-mail e, quando aplicável, o identificador do estabelecimento. A rota `/me` também consulta o banco para confirmar que o usuário continua ativo.

Cadastro de cliente:

```http
POST /api/auth/clientes
Content-Type: application/json
```

```json
{
  "name": "Maria da Silva",
  "email": "maria@email.com",
  "password": "senha123"
}
```

O JWT de um cliente não possui `establishmentId`, pois ele pode realizar agendamentos em diferentes estabelecimentos.

### Autorização e isolamento entre estabelecimentos

- O estabelecimento das operações administrativas é obtido a partir do usuário identificado pelo JWT.
- Os endpoints de funcionários e serviços não recebem `estabelecimentoId` do cliente.
- Apenas usuários `ADMIN` podem consultar ou gerenciar funcionários e serviços.
- Usuários `PROFISSIONAL` podem autenticar e consultar a própria sessão em `/api/auth/me`, mas não possuem acesso às operações administrativas.
- Usuários `CLIENT` podem consultar catálogos, verificar disponibilidade, criar agendamentos e listar seus próprios agendamentos.
- Funcionários e serviços são consultados junto com o identificador do estabelecimento autenticado, impedindo acesso entre estabelecimentos.

## Fluxo de agendamento do MVP

O estabelecimento é identificado na URL. Os serviços e profissionais retornados pelo catálogo pertencem ao estabelecimento e estão ativos.

```http
GET /api/estabelecimentos/{establishmentId}/catalogo/servicos
GET /api/estabelecimentos/{establishmentId}/catalogo/funcionarios
GET /api/estabelecimentos/{establishmentId}/agendamentos/disponibilidade?serviceId=1&professionalId=2&date=2026-09-15
POST /api/estabelecimentos/{establishmentId}/agendamentos
GET /api/agendamentos/me
```

Exemplo de criação:

```json
{
  "serviceId": 1,
  "professionalId": 2,
  "startsAt": "2026-09-15T14:00:00"
}
```

A duração do serviço define automaticamente o término. A API valida o horário geral do estabelecimento, a atividade do serviço e do profissional e conflitos com agendamentos confirmados. A criação bloqueia temporariamente o registro do profissional e revalida a disponibilidade, reduzindo o risco de reservas concorrentes no mesmo período.

Configurações do JWT:

- `JWT_SECRET`: chave com pelo menos 256 bits codificada em Base64. A chave padrão existe somente para desenvolvimento.
- `JWT_EXPIRATION_MINUTES`: validade do token em minutos; o padrão local é 120.

## Organização atual do backend

```text
br.com.salonhub
├── config
├── controller
├── dto
├── entity
├── exception
├── repository
├── security
└── service
```

- `config`: configurações gerais, como a documentação OpenAPI.
- `controller`: endpoints REST.
- `dto`: objetos de entrada, saída e erros da API.
- `entity`: entidades JPA e enums relacionados ao banco.
- `exception`: exceções e tratamento centralizado de erros.
- `repository`: interfaces de acesso aos dados.
- `security`: configuração de autenticação e autorização.
- `service`: regras de negócio e controle transacional.

## Frontend

Aplicação React responsável pela interface administrativa (login, dashboard, funcionários, serviços, atendimentos e comissões), consumindo a API REST do backend.

### Tecnologias

- React 19 + TypeScript
- Vite
- Tailwind CSS 4
- React Router: rotas e proteção por autenticação/perfil
- TanStack Query: cache e sincronização de dados do servidor
- React Hook Form + Zod: formulários e validação
- Axios: cliente HTTP, com interceptor de token e logout automático em `401`

### Organização atual do frontend

```text
frontend/src
├── components
│   ├── layout        # AppLayout (sidebar, shell autenticado)
│   ├── ui             # componentes de UI reutilizáveis
│   └── feedback        # toasts, loaders, estados vazios/erro
├── contexts
│   └── AuthContext.tsx # usuário logado, login/logout, expiração por inatividade
├── features             # organizado por domínio de negócio
│   ├── auth               # login, primeiro acesso, recuperar/alterar senha
│   ├── dashboard           # indicadores administrativos
│   ├── employees            # cadastro e listagem de funcionários
│   ├── services              # cadastro de serviços
│   ├── appointments            # registro e consulta de atendimentos
│   ├── commissions               # relatório de comissões
│   └── establishments              # cadastro de estabelecimentos
├── hooks               # hooks compartilhados (ex: useAuth)
├── lib
│   └── api.ts         # instância do axios e armazenamento do token
├── routes
│   └── ProtectedRoute.tsx # guarda de rota por autenticação e por perfil
└── types
    └── auth.ts        # tipos de usuário e perfis (UserRole)
```

Cada feature reúne suas próprias `pages`, `components`, `services` (chamadas à API) e `types`, mantendo o código organizado por domínio.

### Autenticação no frontend

O `AuthContext` mantém o usuário autenticado em memória, chamando `POST /api/auth/login` e `GET /api/auth/me` do backend. O token JWT recebido é salvo no `localStorage` e enviado automaticamente no cabeçalho `Authorization` de toda requisição pelo interceptor do Axios. Uma resposta `401` limpa o token e redireciona para `/login`; a sessão também expira automaticamente após 30 minutos de inatividade do usuário.

O acesso às rotas é restrito por perfil (`ADMIN` ou `PROFESSIONAL`) por meio do componente `ProtectedRoute`, refletindo as mesmas regras de autorização já aplicadas no backend.
