# Meu Espaço

Meu Espaço é um sistema de agendamento e gestão para salões, barbearias e espaços de estética, desenvolvido como trabalho de conclusão de curso.

O cliente pode consultar os serviços e horários disponíveis, escolher um profissional e marcar um atendimento. Também pode informar o CEP para ordenar os estabelecimentos por proximidade e consultar o endereço e as fotos de cada local.

A equipe acompanha a agenda e registra atendimentos concluídos e faltas. Quem administra o estabelecimento pode cadastrar serviços, organizar os horários da equipe, bloquear períodos da agenda e consultar relatórios de atendimentos, financeiro e comissões. A administração da plataforma tem acesso a um painel para acompanhar a situação dos estabelecimentos cadastrados.

## Como rodar

Com Docker e Compose instalados, execute na raiz do repositório:

```bash
docker compose up --build -d
```

O Compose sobe a interface, a API, o PostgreSQL, o Mailpit e o SeaweedFS. O Flyway aplica as migrações do banco na inicialização da API.

| Serviço | Endereço |
| --- | --- |
| Meu Espaço | [localhost:5173](http://localhost:5173) |
| Swagger UI | [localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| Mailpit (e-mails de desenvolvimento) | [localhost:8025](http://localhost:8025) |
| SeaweedFS (navegador de arquivos) | [localhost:8888](http://localhost:8888) |

Use `docker compose logs -f backend` para acompanhar a inicialização. Para parar os serviços e manter os dados do banco e as imagens nos volumes, execute `docker compose down`.

### Dados de demonstração

Com o ambiente rodando, execute o comando abaixo para adicionar estabelecimentos, profissionais, clientes e agendamentos fictícios:

```bash
docker compose run --rm backend --spring.profiles.active=local,seed
```

As contas abaixo usam a senha `Demo@123`.

| Perfil | E-mail |
| --- | --- |
| Administrador do estabelecimento | `admin@studiobella.demo` |
| Profissional | `camila@studiobella.demo` |
| Cliente | `maria@cliente.demo` |

### Desenvolvimento local

Para rodar a API e a interface fora dos contêineres, instale Java 21 e Node.js 22. Na raiz do repositório, suba os serviços de apoio:

```bash
docker compose up -d postgres mailpit storage
```

Na pasta `backend`, rode `./mvnw spring-boot:run`. No Windows, use `.\mvnw.cmd spring-boot:run`.

Em outro terminal, na pasta `frontend`, rode:

```bash
npm ci
npm run dev
```

O Vite encaminha as requisições de `/api` para a porta 8080. Os padrões do backend permitem rodar o projeto localmente sem criar um `.env`. As variáveis de banco, JWT, e-mail, armazenamento, geocodificação e administração da plataforma estão em [application.properties](backend/src/main/resources/application.properties). Se precisar apontar o frontend para outra API, consulte o [exemplo de ambiente](frontend/.env.example).

## Código e testes

- [backend/](backend/) contém a API em Java 21 com Spring Boot, Spring Security e JPA. O PostgreSQL guarda os dados da aplicação, e as imagens ficam no SeaweedFS por meio de uma API compatível com S3.
- [frontend/](frontend/) contém a interface em React e TypeScript, com Vite e Tailwind CSS.

Na pasta `backend`, com o PostgreSQL rodando, execute `./mvnw test` ou, no Windows, `.\mvnw.cmd test`. Na pasta `frontend`, execute `npm run lint`, `npm test` e `npm run build`. Essas verificações também rodam no [GitHub Actions](.github/workflows/ci.yml).
