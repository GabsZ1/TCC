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
- React no frontend, ainda não iniciado

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

Clientes ainda não fazem parte do sistema. Futuramente serão armazenados em uma tabela própria e, no MVP, não terão login.

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
  "nomeResponsavel": "João da Silva",
  "emailResponsavel": "joao@email.com",
  "senha": "senha123",
  "nomeEstabelecimento": "Barbearia do João",
  "cnpj": "12.345.678/0001-90",
  "emailEstabelecimento": "contato@barbearia.com",
  "telefoneEstabelecimento": "(44) 99999-9999"
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
  "senha": "senha123"
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

O JWT contém os identificadores do usuário e do estabelecimento, o perfil e o e-mail. A rota `/me` também consulta o banco para confirmar que o usuário continua ativo.

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
