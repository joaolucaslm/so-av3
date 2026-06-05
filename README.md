# Simulador de Sistema de Arquivos

**Autores:** João Lucas Lima e Paulo de Tarso

**Link do GitHub** https://github.com/joaolucaslm/so-av3

## Resumo

Este projeto desenvolve um simulador de sistema de arquivos em Java para demonstrar como arquivos e diretórios podem ser organizados, manipulados e recuperados com o auxílio de journaling.

O simulador cria uma imagem persistente do sistema de arquivos em `filesystem.img` e registra as operações em `filesystem.journal`. Dessa forma, é possível executar comandos como criar diretórios, copiar arquivos, apagar arquivos, renomear arquivos e diretórios, listar conteúdo e recuperar o estado do sistema a partir do journal.

## Introdução

O gerenciamento eficiente de arquivos é essencial para o funcionamento dos sistemas operacionais. Um sistema de arquivos organiza dados em estruturas lógicas, permitindo que programas e usuários armazenem, localizem, modifiquem e removam informações de maneira previsível.

Entender como um sistema de arquivos é montado ajuda a compreender conceitos importantes de sistemas operacionais, como hierarquia de diretórios, metadados, persistência, controle de acesso e integridade de dados.

## Objetivo

Desenvolver um simulador de sistema de arquivos em Java com funcionalidades básicas de manipulação de arquivos e diretórios, incluindo suporte a journaling para aumentar a integridade dos dados.

O simulador permite:

- Copiar arquivos.
- Apagar arquivos.
- Renomear arquivos.
- Criar diretórios.
- Apagar diretórios vazios.
- Renomear diretórios.
- Listar arquivos e diretórios.
- Executar comandos em modo shell.
- Persistir o estado em um arquivo que simula a imagem do sistema de arquivos.

## Metodologia

O simulador foi desenvolvido em Java utilizando classes para representar os principais elementos de um sistema de arquivos:

- `File`: representa um arquivo, contendo nome, conteúdo e metadados simples.
- `Directory`: representa um diretório, contendo subdiretórios e arquivos.
- `Journal`: gerencia o log das operações executadas.
- `FileSystemSimulator`: coordena as operações, o shell, a persistência e a recuperação.

Cada operação é implementada como uma chamada de método. No modo shell, o usuário digita comandos e o programa chama internamente os métodos correspondentes.

## Parte 1: Introdução ao Sistema de Arquivos com Journaling

### Sistema de arquivos

Um sistema de arquivos é o componente responsável por organizar dados em dispositivos de armazenamento. Ele define como arquivos são nomeados, onde ficam armazenados, como são agrupados em diretórios e como suas informações são recuperadas.

Em um sistema real, além do conteúdo dos arquivos, também são mantidos metadados como tamanho, permissões, datas de criação/modificação e localização física dos blocos no disco.

Neste simulador, o sistema de arquivos é representado por uma árvore de diretórios. A raiz é `/`, diretórios podem conter outros diretórios e arquivos, e cada arquivo possui um conteúdo textual simples.

### Journaling

Journaling é uma técnica usada para registrar operações antes que elas sejam aplicadas definitivamente ao sistema de arquivos. Esse registro permite recuperar o sistema após falhas, evitando estados inconsistentes.

O fluxo básico usado neste projeto é:

1. Registrar `BEGIN` no journal com a operação e seus parâmetros.
2. Aplicar a operação na estrutura em memória.
3. Registrar `COMMIT` no journal.
4. Salvar a imagem atualizada em `filesystem.img`.

Se o programa for interrompido após o `COMMIT`, mas antes da imagem ser salva, a próxima inicialização lê o journal e reaplica as operações confirmadas que ainda não estavam na imagem.

Tipos comuns de journaling:

- **Write-ahead logging:** registra a intenção da operação antes de alterar os dados principais.
- **Metadata journaling:** registra apenas mudanças nos metadados, como criação ou remoção de diretórios.
- **Full data journaling:** registra metadados e conteúdo dos arquivos, oferecendo maior segurança com maior custo de desempenho.
- **Log-structured file system:** organiza as escritas como um log sequencial, transformando o log na estrutura principal de armazenamento.

Este projeto utiliza uma forma simplificada de write-ahead logging.

## Parte 2: Arquitetura do Simulador

### Estrutura de dados

A estrutura principal é uma árvore:

- A raiz é um objeto `Directory`.
- Cada `Directory` possui um mapa ordenado de subdiretórios.
- Cada `Directory` possui um mapa ordenado de arquivos.
- Cada `File` armazena nome, conteúdo, data de criação e data de atualização.

Essa estrutura facilita operações como buscar caminhos, listar diretórios e validar conflitos de nomes.

### Persistência

O estado do sistema de arquivos é salvo em `filesystem.img` por serialização Java. Esse arquivo representa a imagem persistente do sistema simulado.

O snapshot salvo contém:

- Diretório raiz.
- Última transação aplicada com sucesso.

### Estrutura do journal

O journal é salvo em `filesystem.journal` como texto. Cada transação possui duas linhas:

```text
BEGIN|id|timestamp|OPERACAO|argumentos_em_base64
COMMIT|id
```

Exemplo:

```text
BEGIN|1|1780694265555|CREATE_DIRECTORY|L2RvY3M
COMMIT|1
```

Os argumentos são gravados em Base64 para evitar problemas com espaços e caracteres especiais.

## Parte 3: Implementação em Java

### Classe `FileSystemSimulator`

Implementa o simulador e expõe os métodos principais:

- `createDirectory(String path)`
- `deleteDirectory(String path)`
- `renameDirectory(String path, String newName)`
- `createFile(String path, String content)`
- `copyFile(String sourcePath, String destinationPath)`
- `deleteFile(String path)`
- `renameFile(String path, String newName)`
- `listDirectory(String path)`

Também contém o método `main`, que permite executar o simulador no modo shell ou executar um comando por chamada.

### Classe `File`

Representa um arquivo simulado. Ela armazena:

- Nome.
- Conteúdo textual.
- Tamanho calculado pelo conteúdo.
- Data de criação.
- Data da última atualização.

### Classe `Directory`

Representa um diretório simulado. Ela armazena:

- Nome do diretório.
- Subdiretórios.
- Arquivos.

Os mapas são ordenados para que a listagem tenha saída previsível.

### Classe `Journal`

Gerencia o arquivo de log:

- Cria transações com `BEGIN`.
- Confirma transações com `COMMIT`.
- Lê transações confirmadas.
- Permite consultar as últimas linhas do journal.

## Parte 4: Instalação e funcionamento

### Requisitos

- Java JDK 8 ou superior.
- Terminal ou prompt de comando.

### Compilar

Na raiz do projeto:

```bash
javac -d out src/*.java
```

### Executar em modo shell

```bash
java -cp out FileSystemSimulator
```

Comandos disponíveis no shell:

```text
mkdir <diretorio>
touch <arquivo> [conteudo]
cp <arquivo_origem> <destino>
rm <arquivo>
renamefile <arquivo> <novo_nome>
rmdir <diretorio_vazio>
renamedir <diretorio> <novo_nome>
ls [diretorio]
tree [diretorio]
journal [quantidade_de_linhas]
cd <diretorio>
pwd
exit
```

### Executar um comando por chamada

Também é possível executar uma operação por chamada:

```bash
java -cp out FileSystemSimulator mkdir /docs
java -cp out FileSystemSimulator touch /docs/a.txt "conteudo inicial"
java -cp out FileSystemSimulator cp /docs/a.txt /docs/b.txt
java -cp out FileSystemSimulator renamefile /docs/b.txt c.txt
java -cp out FileSystemSimulator ls /docs
java -cp out FileSystemSimulator rm /docs/c.txt
```

### Exemplo no shell

```text
fs:/> mkdir /docs
Diretorio criado.
fs:/> touch /docs/a.txt "ola mundo"
Arquivo criado.
fs:/> cp /docs/a.txt /docs/b.txt
Arquivo copiado.
fs:/> ls /docs
[FILE] a.txt (9 bytes)
[FILE] b.txt (9 bytes)
fs:/> journal 6
```

### Arquivos gerados

Durante a execução, o programa gera:

- `filesystem.img`: imagem serializada do sistema de arquivos simulado.
- `filesystem.journal`: log textual das operações confirmadas.
- `filesystem.img.tmp`: arquivo temporário usado durante a gravação segura da imagem.

## Resultados esperados

Espera-se que o simulador ajude a visualizar como um sistema de arquivos organiza diretórios e arquivos, além de mostrar a importância do journaling na recuperação de dados após falhas.

Com a execução dos comandos, é possível observar:

- A hierarquia de diretórios.
- A persistência do estado entre execuções.
- O registro das operações no journal.
- A recuperação de operações confirmadas quando a imagem precisa ser reconstruída.

## Estrutura do projeto

```text
.
├── README.md
└── src
    ├── Directory.java
    ├── File.java
    ├── FileSystemSimulator.java
    ├── Journal.java
    └── Operation.java
```
