# SalonHub — direção visual v3

Direção adotada: **editorial utility / cobalt blue**. O objetivo foi sair do visual genérico de dashboard SaaS e aproximar o produto de marcas contemporâneas de beleza, hospitalidade e bem-estar, sem cair em uma estética exclusivamente feminina ou masculina.

## Identidade

- Base clara: branco e cinza-azulado muito suave.
- Cor principal: azul cobalto (`#2457D6`) com marinho profundo para superfícies de alto contraste.
- Verde aparece somente em estados semânticos de sucesso; não faz parte da identidade da marca.
- Tipografia: Manrope na interface e Instrument Serif apenas em momentos editoriais de destaque.
- Bordas finas, sombras discretas, cantos menos exagerados e menos "cards soltos".
- Sem gradientes decorativos, glassmorphism ou blobs típicos de landing pages geradas por IA.

## Decisões de UX

- Sidebar escura com navegação simples e indicador azul de estado ativo.
- Dashboard administrativo com faixa contínua de indicadores em vez de uma grade de cartões iguais.
- Atalhos agrupados em um painel operacional único.
- Fluxo de agendamento consolidado em um único painel dividido por etapas, reduzindo fragmentação visual.
- Login/cadastro com composição editorial e uma agenda ilustrativa, conectando a identidade visual diretamente ao uso real do sistema.
- Tema escuro removido para reduzir complexidade visual e evitar combinações de contraste inconsistentes.

## Acessibilidade

A paleta possui teste automatizado de contraste em `src/themeContrast.test.ts`. As combinações de texto usadas em superfícies, botões, estados e sidebar devem atingir no mínimo **WCAG AA (4.5:1)** para texto normal.

### Verificação atual

Na revisão desta versão foram verificadas 22 combinações críticas de foreground/background. Todas passaram em **4.5:1 ou mais**; a menor razão encontrada entre os pares testados foi **4.79:1** (`text-400` sobre superfície branca). O teste automatizado mantém esses pares registrados para evitar regressões futuras.
