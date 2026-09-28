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

Na raiz do projeto, inicie o PostgreSQL e o Mailpit (servidor de e-mail de desenvolvimento):

```bash
docker compose up -d
```

Todos os e-mails enviados pelo backend (primeiro acesso de funcionário e recuperação de senha) ficam visíveis na caixa de entrada do Mailpit em <http://localhost:8025>. Nenhum e-mail sai para a internet.

O mesmo comando sobe o **SeaweedFS** (container `salonhub-storage`), o armazenamento de imagens compatível com S3:

- API S3: <http://localhost:8333> (chave `salonhub_s3`, segredo `salonhub_s3_pass`)
- Navegador de arquivos: <http://localhost:8888/buckets/salonhub-media/>
- O bucket `salonhub-media` é criado automaticamente na subida (e, se faltar, pelo backend no primeiro envio).
- As credenciais e o nome do bucket podem ser trocados pelas variáveis `S3_ACCESS_KEY`, `S3_SECRET_KEY` e `S3_BUCKET`.
- Os arquivos ficam no volume Docker `storage_data`.
- Por que não MinIO: em setembro de 2026 as imagens públicas do MinIO saíram do Docker Hub e do Quay.io. O backend fala S3 puro, então qualquer servidor compatível (SeaweedFS, AWS S3, Cloudflare R2) serve só mudando a configuração.

Como as imagens são organizadas (migration V27):

- O **arquivo** vai para o bucket, no caminho `establishments/{idDoEstabelecimento}/media/{uuid}.{jpg|png|webp}`.
- O **PostgreSQL** guarda só os metadados na tabela `media` (`establishment_id`, `storage_key`, `original_filename`, `mime_type`, `size_bytes`, `uploaded_by`, `created_at`). A URL completa nunca é salva, só a `storage_key`, para que o armazenamento possa mudar (ex.: S3) sem alterar registros.
- As entidades apontam para `media`: hoje, `users.photo_id` (foto do funcionário).
- Camadas: `MediaService` (validação, registro e exclusão) → `StorageService` (interface) → `S3StorageService` (cliente S3 da biblioteca do MinIO). Nos testes, `app.storage.type=memory` usa o `InMemoryStorageService`.
- Se o armazenamento estiver fora do ar, o backend sobe normalmente; só as operações de imagem respondem 503 com mensagem amigável.
- Configuração: `STORAGE_TYPE` (`s3` ou `memory`), `S3_ENDPOINT` (padrão `http://localhost:8333`), `S3_ACCESS_KEY`, `S3_SECRET_KEY` e `S3_BUCKET`.

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

### Estados operacionais do estabelecimento

- **Agendamentos bloqueados:** impede novas reservas e remarcações, preservando todos os atendimentos existentes.
- **Pausado:** só pode ser aplicado quando não há atendimentos confirmados e bloqueia o acesso dos profissionais; o administrador do estabelecimento continua com acesso.
- **Site desativado:** ação do administrador do sistema que bloqueia todos os usuários vinculados e cancela definitivamente os atendimentos confirmados com o motivo `Site desativado`.

Os três estados são reversíveis, mas reativar o site não restaura agendamentos cancelados. Quando o administrador do sistema aplica o bloqueio de agendamentos ou a pausa, somente ele pode remover essa restrição.

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

Alteração de senha (qualquer perfil autenticado):

```http
PATCH /api/auth/senha
Authorization: Bearer SEU_TOKEN
Content-Type: application/json
```

```json
{
  "currentPassword": "senha123",
  "newPassword": "novaSenha456",
  "newPasswordConfirmation": "novaSenha456"
}
```

Respostas: `204` senha alterada; `400` senha atual incorreta, confirmação divergente, nova senha igual à atual ou fora da política (8 a 72 caracteres); `401` sessão inválida ou usuário inativo. No frontend, a tela fica em `/alterar-senha`, no grupo "Conta" do menu.

Recuperação de senha (rotas públicas, US 2.20):

```http
POST /api/auth/recuperar-senha          {"email": "joao@email.com"}
GET  /api/auth/redefinir-senha?token=…  (valida o link ao abrir a tela)
POST /api/auth/redefinir-senha          {"token": "…", "newPassword": "…", "newPasswordConfirmation": "…"}
```

- O link é enviado por e-mail, vale 15 minutos, é de uso único e um novo pedido invalida os anteriores.
- O mesmo e-mail só pode pedir um novo link a cada 2 minutos (`429 Too Many Requests`).
- O banco guarda apenas o hash SHA-256 do token (tabela `password_reset_tokens`, migration V23).
- No frontend: link "Esqueci minha senha" no login → `/recuperar-senha` → e-mail → `/redefinir-senha?token=…`.

Configuração de e-mail (variáveis de ambiente): `MAIL_HOST` (padrão `localhost`), `MAIL_PORT` (padrão `1025`), `MAIL_FROM`, `FRONTEND_URL` (padrão `http://localhost:5173`, usado para montar os links), `PASSWORD_RESET_EXPIRATION_MINUTES` e `PASSWORD_RESET_COOLDOWN_SECONDS`. Para um SMTP real (ex.: Gmail, SendGrid), configure também `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` e `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`. Se o servidor de e-mail estiver fora do ar, a operação não falha: o erro fica registrado no log.

Cadastro de cliente:

```http
POST /api/auth/clientes
Content-Type: application/json
```

```json
{
  "name": "Maria da Silva",
  "email": "maria@email.com",
  "phone": "(44) 99999-1234",
  "password": "senha123",
  "passwordConfirmation": "senha123"
}
```

O telefone é salvo só com dígitos (DDD + 8 ou 9 dígitos) e a confirmação precisa ser igual à senha ("As senhas não coincidem!").

Conta do cliente (US 2.24 a 2.26), sempre do próprio cliente autenticado:

```http
GET    /api/clientes/me   → nome, e-mail e telefone (nunca a senha)
PUT    /api/clientes/me   {"name": "...", "email": "...", "phone": "..."}
DELETE /api/clientes/me   {"password": "senha atual"}
```

A exclusão exige a senha, cancela os agendamentos futuros confirmados, bloqueia o login, anonimiza os dados pessoais e libera o e-mail para um novo cadastro. A linha do usuário é mantida para que o histórico de atendimentos do estabelecimento continue íntegro (migration V25). No frontend: menu "Meu perfil" (`/meu-perfil`).

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

### Funcionários (US 2.1, 2.2 e 2.6) e perfil do funcionário

Quem administra cada dado:

| Dado | Administrador | Próprio funcionário |
|---|---|---|
| Nome | cadastra e altera | só vê |
| CPF e data de admissão | cadastra (obrigatórios); se estiverem vazios em cadastros antigos, preenche **uma única vez** | só vê |
| E-mail de acesso | cadastra | altera, confirmando com a senha atual |
| Ocupação | cadastra | altera |
| Telefone, Instagram, endereço, foto | — | altera |
| Ativo/inativo, serviços, remoção | altera | — |

Depois de gravados, CPF e data de admissão não podem ser alterados por ninguém; o frontend pede dupla confirmação (revisão + redigitar o CPF) antes de gravá-los.

```http
POST /api/funcionarios                 {"name","email","occupation","cpf","hireDate"}   (admin)
PUT  /api/funcionarios/{id}            {"name","cpf?","hireDate?"}                      (admin)
GET  /api/funcionarios/me/perfil                                                         (profissional)
PUT  /api/funcionarios/me/perfil       {"email","occupation","phone?","instagram?","address?","currentPassword?"}
PUT  /api/funcionarios/me/foto         multipart, campo "file" (JPG/PNG/WEBP até 2 MB)   (profissional)
DELETE /api/funcionarios/me/foto                                                         (profissional)
GET  /api/funcionarios/{id}/foto       admin do estabelecimento ou o próprio profissional
```

No frontend, o funcionário acessa "Meu perfil" (`/meu-perfil`), que também tem o atalho para alterar a senha.

### Atendimentos realizados do profissional (US 2.15)

```http
GET /api/profissionais/me/atendimentos-realizados?startDate=2026-03-01&endDate=2026-03-31&serviceId=1
Authorization: Bearer TOKEN_DO_PROFISSIONAL
```

Retorna apenas atendimentos **concluídos** do próprio profissional (o ID vem sempre do token), filtrados pela **data de conclusão**, do mais recente para o mais antigo, com valor, percentual e valor da comissão congelados no agendamento, um resumo (quantidade, soma dos valores, soma das comissões) e os tipos de serviço já realizados para o filtro. Sem período, considera o mês corrente. No frontend: menu "Atendimentos realizados" (`/meus-atendimentos`).

### Atendimentos finalizados do estabelecimento (US 2.13)

```http
GET /api/estabelecimentos/me/atendimentos-realizados?startDate=2026-03-01&endDate=2026-03-31&professionalId=2&page=0&size=20
Authorization: Bearer TOKEN_DO_ADMIN
```

Somente administrador. Lista atendimentos **finalizados** (invalidados e cancelados ficam de fora) com funcionário, data de conclusão, serviço, cliente, valor e comissão, do mais recente para o mais antigo. Filtros por funcionário (apenas do próprio estabelecimento) e por período de conclusão, combináveis; **sem filtros, retorna todo o histórico**. Paginado (padrão 20, máximo 100 por página); `totalServiceValue` e `totalCommission` somam todos os resultados do filtro, não só a página. No frontend: menu "Atendimentos", aba "Atendimentos realizados" (a aba "Visão por status" mantém o relatório de volume por situação).

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
