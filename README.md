# MCreator MCP Bridge

Plugin Java para o MCreator que expõe um servidor [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) local. O núcleo JSON-RPC 2.0 não tem dependências externas e é reutilizado pelos transportes stdio e HTTP.

Compatibilidade testada: MCreator **2026.2**, build **2026.2.33518**. O manifesto também declara compatibilidade com 2026.1 e 2026.2.

## Estado desta primeira versão

O protocolo e a descoberta das tools estão funcionais: `initialize`, `ping`, `tools/list` e `tools/call`. `McpJsonRpcHandler` é a camada comum independente de transporte. As tools `list_mod_elements`, `create_item`, `create_mod_element`, `validate_procedure_xml`, `create_procedure`, `attach_item_procedure` e `get_workspace_info` executam a ponte na EDT e acessam o Workspace aberto do MCreator.

`create_mod_element` aceita `name` e `elementType`. Nesta etapa, os únicos tipos habilitados são `item` e `block`: o core usa o mesmo fluxo de defaults de `BlockGUI` nos seus geradores de packs. Tipos como `livingentity` continuam deliberadamente bloqueados até terem um esquema próprio, validado, para modelo, textura e demais configurações.

Além dessas tools guiadas, a camada dinâmica `create_element_from_json` e `update_element_from_json` não mantém uma lista fixa de propriedades permitidas. Ela aceita qualquer campo serializável do tipo registrado no runtime, aplica os valores sobre o default real do MCreator, desserializa usando `ModElementManager.fromJSONtoGeneratableElement`, executa o `GEValidator`, persiste e regenera. Isso é o caminho recomendado para configurar todos os campos que o MCreator (ou um plugin) realmente persiste.

Os pontos de integração estão concentrados em `MCreatorToolBridge.java` e usam APIs do Workspace e ModElement confirmadas no fonte do MCreator.

## Pré-requisitos

- Checkout do core em <https://github.com/MCreator/MCreator>, exatamente no tag `2026.2.33518` quando a instalação usada for MCreator 2026.2.33518.
- JDK 25 (a JBR da instalação do MCreator pode ser usada) e Gradle compatível com o checkout do MCreator.
- Java plugins ativados nas preferências do MCreator (suportados desde 2022.2).

## Compilar e instalar

1. Obtenha o checkout que corresponde à instalação em que o plugin vai rodar. Para MCreator 2026.2.33518:

   ```powershell
   git clone https://github.com/MCreator/MCreator.git C:/dev/MCreator-2026.2.33518
   git -C C:/dev/MCreator-2026.2.33518 checkout 2026.2.33518
   ```

2. Copie `gradle.properties.example` para `gradle.properties`, aponte para esse checkout e para a JBR da instalação:

   ```properties
   mcreator_path=C:/dev/MCreator-2026.2.33518
   mcreator_jdk_path=C:/Users/Slayer/Desktop/MCreator20262/jdk
   org.gradle.java.installations.paths=C:/Users/Slayer/Desktop/MCreator20262/jdk
   ```

3. Na raiz deste projeto, execute:

   ```powershell
   gradle jar
   ```

4. Instale `build/libs/mcreator-mcp-bridge.zip` pelo gestor de plugins do MCreator.

### Modelo de build do core atual

O core atual do MCreator é um projeto Gradle raiz; incluí-lo como `:MCreator` subprojeto, como o DemoJavaPlugin faz, quebra a configuração `idea` no Gradle atual. Não há, nesta revisão do core, uma publicação Maven do MCreator, um `includeBuild` para plugins de terceiros ou um exemplo interno mais recente que resolva isso. O MCreatorMCP ainda usa o mesmo padrão de subprojeto do DemoJavaPlugin, portanto não é compatível com este modelo de build por si só.

Este plugin usa a saída de compilação oficial do core em `<mcreator_path>/build/classes/java/main` como dependência `compileOnly`. Antes de compilar o plugin, a tarefa `compileMCreatorCore` executa `<mcreator_path>/gradlew compileJava`; assim, `gradle jar` não depende de uma etapa manual. As bibliotecas `lib/` e Log4j são somente dependências de compilação: não são empacotadas no ZIP, pois o MCreator já as fornece em execução.

É importante que o checkout seja o mesmo release da instalação. O checkout `c93379e1` (main posterior) adicionou `Generator.refreshWorkspaceSourceInfo()`, mas o binário oficial 2026.2.33518 não possui esse método; misturar os dois causa `NoSuchMethodError`. O instalador Windows não disponibiliza as classes do core em um JAR separado para consumo pelo Gradle, portanto compilar contra o tag oficial correspondente é a fonte de verdade viável. O ciclo de persistência deste plugin agora segue a sequência que `ModElementGUI` do tag 2026.2.33518 realmente usa: armazenar, gerar, recarregar ícone/reinicializar e atualizar as abas.

Após atualizar o checkout do MCreator, execute novamente `gradle jar`. A tarefa primeiro pedirá ao wrapper do MCreator para atualizar as classes do core e então recompilará o plugin. Se a API interna mudar, a falha de compilação apontará diretamente a incompatibilidade.

## Transporte HTTP local (recomendado)

O plugin inicia HTTP por padrão em `http://127.0.0.1:39217/mcp`. Ele usa o Streamable HTTP do MCP, em modo stateless: cada mensagem JSON-RPC é enviada por `POST /mcp` e recebe uma resposta `application/json`. Não há dependência Gradle nova: `HttpServer` vem do módulo JDK `jdk.httpserver`.

O servidor nunca escuta em `0.0.0.0`; é ligado explicitamente a `127.0.0.1`. Também rejeita `Origin` que não seja `http://localhost:<porta>` ou `http://127.0.0.1:<porta>` e exige um token em todas as requisições.

Configure estas propriedades da JVM do MCreator:

```text
-Dmcreator.mcp.http.enabled=true
-Dmcreator.mcp.http.port=39217
-Dmcreator.mcp.http.token=troque-por-um-token-longo-e-aleatorio
--add-modules=jdk.httpserver
```

`mcreator.mcp.http.enabled` tem padrão `true`; use `false` para desligar. A porta padrão é `39217`. Se a propriedade `mcreator.mcp.http.token` não for configurada, o plugin gera um token uma única vez e o salva em `C:\Users\Slayer\.mcreator\mcp-bridge\token.txt` (em geral, `<user.home>/.mcreator/mcp-bridge/token.txt`). Reinicializações posteriores reutilizam o mesmo valor. No Windows, a proteção do arquivo depende das permissões padrão da pasta do usuário; no Linux/macOS, o plugin aplica `rw-------` quando o sistema de arquivos oferece permissões POSIX.

Para clientes locais, uma IA de código como Codex ou Claude Code pode ler esse arquivo antes de fazer chamadas HTTP, sem copiar o token do log manualmente:

```powershell
$token = Get-Content "$env:USERPROFILE\.mcreator\mcp-bridge\token.txt" -Raw
$headers = @{ "Authorization" = "Bearer $($token.Trim())" }
```

Para usar deliberadamente um token manual e fixo, passe `-Dmcreator.mcp.http.token=SEU_TOKEN`. Essa propriedade tem prioridade absoluta: o arquivo persistido não é criado nem sobrescrito nesse caso. O log informa a origem do token em toda inicialização.

Envie o token por um dos headers abaixo:

```http
Authorization: Bearer SEU_TOKEN
```

ou:

```http
X-MCreator-MCP-Token: SEU_TOKEN
```

### Cliente MCP

Clientes que suportam Streamable HTTP devem apontar para `http://127.0.0.1:39217/mcp`, não apenas para a raiz da porta. Por exemplo, uma configuração HTTP típica do Claude Code é:

```json
{
  "mcpServers": {
    "mcreator": {
      "type": "http",
      "url": "http://127.0.0.1:39217/mcp",
      "headers": {
        "Authorization": "Bearer SEU_TOKEN"
      }
    }
  }
}
```

Em Claude Desktop, use a opção/configuração de servidor HTTP disponível na sua versão e informe a mesma URL e header. Se a versão instalada só aceitar servidores stdio, ela não poderá anexar-se diretamente a uma instância do MCreator já aberta; use um cliente com Streamable HTTP, como o MCP Inspector, ou atualize/empregue um proxy cliente compatível.

O endpoint `GET /mcp` responde `405 Allow: POST`: SSE é opcional no Streamable HTTP e esta versão não tem notificações iniciadas pelo servidor. O comentário em `McpHttpServer.java` marca o local para acrescentar `text/event-stream` quando houver progresso, assinaturas ou eventos do Workspace.

Teste por `curl`/PowerShell depois de iniciar o MCreator:

```powershell
$headers = @{ Authorization = 'Bearer SEU_TOKEN'; Accept = 'application/json, text/event-stream'; 'Content-Type' = 'application/json' }
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_workspace_info","arguments":{}}}'
```

A primeira resposta contém as tools disponíveis. A segunda retorna os metadados reais do workspace aberto.

### Livros Patchouli

Para Patchouli 1.21.x, o bridge escreve livros de mod em duas árvores exigidas pelo Patchouli: `src/main/resources/data/<modid>/patchouli_books/<bookId>/book.json` declara o livro, enquanto categorias e entradas ficam em `src/main/resources/assets/<modid>/patchouli_books/<bookId>/en_us/`. O mod final ainda precisa declarar Patchouli como dependência; estas tools somente produzem os arquivos de dados.

Crie o livro, a categoria e a entrada nesta ordem. `create_patchouli_category` usa `minecraft:book` como ícone obrigatório da categoria, pois a API desta primeira versão não recebe um ícone de categoria. A entrada exige que `icon` corresponda a um item já existente no workspace, no formato `<modid>:<registry_name>`.

```powershell
$book = @{ jsonrpc = '2.0'; id = 60; method = 'tools/call'; params = @{ name = 'create_patchouli_book'; arguments = @{ bookId = 'test_book'; name = 'MicroTech Guide' } } } | ConvertTo-Json -Depth 8 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $book

$category = @{ jsonrpc = '2.0'; id = 61; method = 'tools/call'; params = @{ name = 'create_patchouli_category'; arguments = @{ bookId = 'test_book'; categoryId = 'basics'; name = 'Basics'; description = 'Welcome to MicroTech.' } } } | ConvertTo-Json -Depth 8 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $category

$entry = @{ jsonrpc = '2.0'; id = 62; method = 'tools/call'; params = @{ name = 'create_patchouli_entry'; arguments = @{ bookId = 'test_book'; categoryId = 'basics'; entryId = 'test_resource_item'; name = 'Test Resource Item'; icon = 'microtech:test_resource_item'; texts = @('First page.', 'Second page.') } } } | ConvertTo-Json -Depth 8 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $entry
```

Cada valor em `texts` é salvo como uma página `{ "type": "patchouli:text", "text": "..." }`. As três tools rejeitam IDs inválidos, livros/categorias ausentes e arquivos duplicados sem sobrescrever conteúdo existente.

### Resources do workspace

Além das tools, o servidor anuncia a capability MCP `resources`. `resources/list` retorna um recurso somente-leitura para cada `ModElement` aberto, com URI `mcreator://element/<nome>`. `resources/read` recebe essa URI e devolve o conteúdo textual do arquivo persistido real `elements/<nome>.mod.json`; para procedures, esse JSON inclui o XML Blockly. As duas operações não alteram o workspace.

```powershell
$token = (Get-Content "$env:USERPROFILE\.mcreator\mcp-bridge\token.txt" -Raw).Trim()
$headers = @{ Authorization = "Bearer $token" }

$listRequest = @{ jsonrpc = '2.0'; id = 12; method = 'resources/list'; params = @{} } | ConvertTo-Json -Compress
$resources = Invoke-RestMethod http://127.0.0.1:39217/mcp -Method Post -Headers $headers -ContentType 'application/json' -Body $listRequest
$resources.result.resources | ConvertTo-Json -Depth 5

$readRequest = @{ jsonrpc = '2.0'; id = 13; method = 'resources/read'; params = @{ uri = 'mcreator://element/HooktestProcedure' } } | ConvertTo-Json -Depth 5 -Compress
$definition = Invoke-RestMethod http://127.0.0.1:39217/mcp -Method Post -Headers $headers -ContentType 'application/json' -Body $readRequest
$definition.result.contents[0].text
```

Uma URI fora do esquema `mcreator://element/`, um nome inexistente ou um arquivo ainda não persistido retorna erro JSON-RPC claro. A resolução do `Workspace` é feita na EDT; a leitura do arquivo é feita depois, fora da EDT, para não bloquear a interface Swing.

### Diagnóstico de assets GeckoLib Reborn

O bridge oferece suporte **somente de leitura/diagnóstico** para GeckoLib Reborn por meio de `check_geckolib_block_assets`. A tool recebe `modelName` (nome base, sem `.geo.json`) e informa se o runtime registrou `animatedblock`, além dos modelos, textura e animações encontrados. Ela nunca cria, importa, altera ou gera arquivos.

Para o modelo e a animação, a busca reproduz a ordem usada pelo Java gerado pelo GeckoLib Reborn: `geo/block`, depois `geo/entity`, depois `geo` (e as pastas equivalentes sob `animations`). A textura consultada é `textures/block/<modelName>.png`.

```powershell
$body = @{ jsonrpc = '2.0'; id = 90; method = 'tools/call'; params = @{ name = 'check_geckolib_block_assets'; arguments = @{ modelName = 'storage' } } } | ConvertTo-Json -Depth 8 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $body
```

O resultado contém `model`, `texture` e `animation`, cada um com o estado `found`, caminho selecionado quando houver, e a lista de caminhos testados para modelo/animação. Isso é útil antes de criar o elemento manualmente pela UI do MCreator.

Por limitação da API pública do GeckoLib Reborn, a criação de `animatedblock`, `animateditem`, `animatedentity` e `animatedarmor` permanece **manual pela UI do MCreator**. A GUI do plugin não expõe setters/validação headless públicos; automatizar essa escrita exigiria reflection sobre campos privados de um plugin de terceiro com manutenção instável, o que este bridge não faz.

### Nerdy's Curios API Plugin — integração best-effort

Quando o plugin **New Curios API Plugin 1.5** estiver instalado e a dependência de workspace `curios_api` estiver habilitada, o bridge oferece as tools `create_curios_slot` e `create_curios_bauble`. Elas usam reflection apenas sobre os campos públicos das classes de armazenamento do plugin de terceiro e verificam em runtime se `curiosslot`/`curiosbauble` foram registrados; atualizações do plugin podem quebrar essa integração.

`create_curios_slot` recebe `name`, `textureName` (textura SCREEN já importada, sem `.png`) e, opcionalmente, `slotName`, `amount`, `modelToggling`, `changeOrder` e `slotOrder`. `create_curios_bauble` recebe `name`, `itemName` (um `item` já existente) e `slotType`; também aceita `slotAmount`, `addSlot`, `enderMask`, `friendlyPigs` e `snowWalk`. A tool impede usar o mesmo item em dois baubles.

Modelos Java customizados e hooks de procedure Curios permanecem manuais na UI nesta versão. Os blocos Blockly `is_curio` e `curio_equipped_foreach` já podem ser usados em XML enviado a `validate_procedure_xml` e `create_procedure` quando o plugin está carregado.

### API dinâmica de elementos e assets

Use esta API quando quiser configurar campos além dos poucos atalhos de criação. O fluxo é sempre o mesmo:

1. Chame `list_element_types` para descobrir os tipos disponíveis nesta instalação, inclusive os adicionados por plugins.
2. Chame `get_element_schema` para obter o envelope/default atual do tipo.
3. Crie com `create_element_from_json`, ou leia um elemento real com `get_element_definition` e altere somente os campos desejados com `update_element_from_json`.

O parâmetro `definition` é um objeto de *patch*: campos escalares e listas substituem o valor anterior; objetos aninhados são combinados recursivamente. Não envie `_type`, `_fv` ou um envelope `.mod.json` inteiro — esses campos são controlados pelo MCreator. Exemplo conceitual para qualquer campo que o schema de `item` realmente exponha:

```powershell
$definition = @{
  texture = 'solar_sword'
  # Adicione aqui os campos reais retornados pelo schema da sua instalação.
}
$body = @{ jsonrpc = '2.0'; id = 100; method = 'tools/call'; params = @{ name = 'create_element_from_json'; arguments = @{ elementType = 'item'; name = 'SolarSword'; definition = $definition } } } | ConvertTo-Json -Depth 20 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $body
```

O resultado devolve a definição que foi efetivamente aceita pelo serializer do MCreator. Se um plugin de terceiro registrar um tipo e uma classe de armazenamento serializável normal, ele participa do mesmo fluxo. Campos que existem apenas no estado privado de uma GUI de plugin não podem ser configurados por uma API JSON segura e serão explicitamente rejeitados ou simplesmente não aparecerão na definição retornada; o bridge não usa reflection privada para contornar isso.

`list_workspace_assets` lista os arquivos importados sob `src/main/resources`, como texturas PNG, modelos `.geo.json`, animações e arquivos de dados. `read_workspace_asset(path)` lê somente assets textuais (`.json`, `.mcmeta`, `.lang` e `.txt`), por exemplo:

```powershell
$body = @{ jsonrpc = '2.0'; id = 101; method = 'tools/call'; params = @{ name = 'read_workspace_asset'; arguments = @{ path = 'assets/microtech/geo/block/storage.geo.json' } } } | ConvertTo-Json -Depth 8 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $body
```

Essas tools não importam nem criam arquivos de asset: você pode importá-los pela UI do MCreator e a IA então os localiza, lê os modelos textuais e os referencia nos campos que o schema do elemento aceitar.

### Código Java e snippets em procedures

O bridge agora expõe três tools para o tipo nativo `code` do MCreator: `create_code_element`, `read_code_element` e `write_code_element`. Um elemento `code` é uma classe Java completa, criada pelo mesmo ciclo que `CustomElementGUI`: o MCreator gera o arquivo inicial, o elemento fica **code-locked** e o bridge substitui apenas aquele arquivo. Ele não permite escrever diretamente em código gerado de itens, blocos ou procedures, pois uma regeneração do MCreator poderia sobrescrever essas alterações.

`create_code_element` recebe `name` e `source` (o arquivo Java inteiro). Em seguida, a IA pode consultar ou substituir o mesmo arquivo com `read_code_element` e `write_code_element`.

```powershell
$source = @'
package net.mcreator.microtech;

public final class HealthHelper {
    private HealthHelper() { }
}
'@
$body = @{ jsonrpc = '2.0'; id = 110; method = 'tools/call'; params = @{ name = 'create_code_element'; arguments = @{ name = 'HealthHelper'; source = $source } } } | ConvertTo-Json -Depth 8 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $body
```

Para inserir Java diretamente em uma procedure, use `append_procedure_java_snippet`. Ela usa o bloco Blockly oficial `java_code` do próprio MCreator e acrescenta o código ao **fim** da cadeia do `event_trigger`; não substitui blocos existentes nem aceita um caminho XML arbitrário. O Java é emitido literalmente pelo gerador. Portanto, a tool valida o XML/Blockly, mas a sintaxe Java e as variáveis disponíveis só são verificadas quando o workspace for compilado. Use somente variáveis que o trigger da procedure fornece.

Exemplo para uma procedure cujo trigger fornece `entity`, como um gancho de item:

```powershell
$snippet = 'if (entity instanceof net.minecraft.world.entity.LivingEntity livingEntity) { livingEntity.heal(4.0F); }'
$body = @{ jsonrpc = '2.0'; id = 111; method = 'tools/call'; params = @{ name = 'append_procedure_java_snippet'; arguments = @{ procedureName = 'HealOnUse'; code = $snippet } } } | ConvertTo-Json -Depth 8 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $body
```

Um *snippet* não é um tipo separado de elemento no MCreator: é esse bloco `java_code` dentro do XML Blockly. A ordem recomendada é criar/validar a procedure, anexá-la ao hook compatível do item ou bloco e, então, acrescentar o snippet. Faça uma compilação do workspace depois para capturar erros Java.

### Validar XML de procedure

`validate_procedure_xml` é somente leitura: recebe `xml` (string obrigatória), cria uma `Procedure` e um `ModElement` temporários em memória e compila o XML Blockly contra o generator do workspace aberto. Ela não adiciona elementos, não grava `.mod.json` e não gera código.

Uma resposta bem-sucedida traz `valid`, `notes` e, quando `valid` é `true`, `dependencies`, `return_type` e `trigger`. Mensagens de notas e do parser são localizadas pelo MCreator; por isso podem variar conforme o idioma da instalação.

XML padrão válido:

```powershell
$xml = '<xml xmlns="https://developers.google.com/blockly/xml"><block type="event_trigger" deletable="false" x="40" y="40"><field name="trigger">no_ext_trigger</field></block></xml>'
$body = @{ jsonrpc = '2.0'; id = 3; method = 'tools/call'; params = @{ name = 'validate_procedure_xml'; arguments = @{ xml = $xml } } } | ConvertTo-Json -Depth 8 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $body
```

Resposta observada:

```json
{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\"valid\":true,\"notes\":[],\"dependencies\":[],\"return_type\":null,\"trigger\":\"no_ext_trigger\"}"}]}}
```

XML malformado retorna erro JSON-RPC claro, sem exceção não tratada:

```json
{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"validate_procedure_xml","arguments":{"xml":"<xml><block></xml>"}}}
```

Resposta observada (a mensagem pode ser traduzida):

```json
{"jsonrpc":"2.0","id":4,"error":{"code":-32600,"message":"Invalid Blockly XML: ..."}}
```

Um XML estruturalmente válido com bloco inexistente é rejeitado como erro de validação, para impedir que uma futura tool de escrita descarte lógica silenciosamente:

```json
{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"validate_procedure_xml","arguments":{"xml":"<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\"><field name=\"trigger\">no_ext_trigger</field><next><block type=\"mcp_nonexistent_block\"/></next></block></xml>"}}}
```

Resposta observada:

```json
{"jsonrpc":"2.0","id":5,"result":{"content":[{"type":"text","text":"{\"valid\":false,\"notes\":[{\"level\":\"WARNING\",\"message\":\"...unknown...\"},{\"level\":\"ERROR\",\"message\":\"Unknown Blockly procedure block type: mcp_nonexistent_block\"}]}"}]}}
```

### Criar uma procedure

`create_procedure` recebe `name` e `xml`, ambos obrigatórios. Antes de criar um `ModElement`, ela usa exatamente a mesma validação de `validate_procedure_xml`. Qualquer nota `ERROR` — inclusive bloco Blockly desconhecido — retorna erro JSON-RPC e não adiciona, gera nem persiste elemento algum.

Quando a validação passa, a tool cria um elemento `procedure`, atribui `procedurexml`, adiciona-o ao workspace e usa a ordem do teste de integração `GTVariables`: `generateElement(procedure, true)` e, depois, `storeModElement(procedure)`. A resposta contém `name`, `type`, `registryName`, `created`, `dependencies`, `return_type` e `trigger`.

Criar a procedure vazia padrão:

```powershell
$xml = '<xml xmlns="https://developers.google.com/blockly/xml"><block type="event_trigger" deletable="false" x="40" y="40"><field name="trigger">no_ext_trigger</field></block></xml>'
$body = @{ jsonrpc = '2.0'; id = 6; method = 'tools/call'; params = @{ name = 'create_procedure'; arguments = @{ name = 'MyProcedure'; xml = $xml } } } | ConvertTo-Json -Depth 8 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $body
```

Resposta observada:

```json
{"jsonrpc":"2.0","id":6,"result":{"content":[{"type":"text","text":"{\"name\":\"MyProcedure\",\"type\":\"procedure\",\"registryName\":\"my_procedure\",\"created\":true,\"dependencies\":[],\"return_type\":null,\"trigger\":\"no_ext_trigger\"}"}]}}
```

Um bloco inexistente é rejeitado antes de escrever no workspace:

```json
{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"create_procedure","arguments":{"name":"MustNotBeCreated","xml":"<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\"><field name=\"trigger\">no_ext_trigger</field><next><block type=\"mcp_nonexistent_block\"/></next></block></xml>"}}}
```

Resposta observada e verificação posterior por `list_mod_elements`:

```json
{"jsonrpc":"2.0","id":7,"error":{"code":-32600,"message":"Procedure XML validation failed: [... ERROR: Unknown Blockly procedure block type: mcp_nonexistent_block ...]"}}
{"jsonrpc":"2.0","id":8,"result":{"content":[{"type":"text","text":"{\"elements\":[{\"name\":\"MyProcedure\",\"type\":\"procedure\"}],\"count\":1}"}]}}
```

### Anexar uma procedure a um item

`attach_item_procedure` recebe `itemName`, `hook` e `procedureName`. Os três parâmetros são obrigatórios. A tool confirma que o elemento de destino é um `item`, que a procedure existe e que o hook é conhecido antes de modificar qualquer dado. Em seguida, reproduz o critério de `AbstractProcedureSelector`: todas as dependências exigidas pela procedure devem ser fornecidas pelo evento e, quando o hook exige retorno, o `VariableType` deve ser exatamente compatível.

Hooks suportados — todos são os campos públicos de tipo `net.mcreator.element.parts.procedure.Procedure` declarados diretamente por `Item` no core 2026.2:

- `onRightClickedInAir`, `onCrafted`, `onEntitySwing`, `onDroppedByPlayer`, `onFinishUsingItem` e `onRangedItemUsed`: `x`, `y`, `z`, `world`, `entity`, `itemstack`; qualquer retorno.
- `onRightClickedOnBlock`: os mesmos, mais `direction` e `blockstate`; retorno `actionresulttype` ou nenhum retorno.
- `onEntityHitWith`: `x`, `y`, `z`, `world`, `entity`, `sourceentity`, `itemstack`; qualquer retorno.
- `onItemInInventoryTick` e `onItemInUseTick`: dependências básicas mais `slot`; qualquer retorno.
- `onStoppedUsing` e `everyTickWhileUsing`: dependências básicas mais `time`; qualquer retorno.
- `onItemEntityDestroyed`: dependências básicas mais `damagesource`; qualquer retorno.
- `rangedUseCondition`: dependências básicas; retorno obrigatório `logic`.

Os campos `specialInformation`, `glowCondition` e `openGUIOnRightClick` usam subclasses especializadas (`StringListProcedure`/`LogicProcedure`), portanto estão fora do escopo desta tool de `Procedure` simples.

Exemplo compatível:

```powershell
$body = @{ jsonrpc = '2.0'; id = 9; method = 'tools/call'; params = @{ name = 'attach_item_procedure'; arguments = @{ itemName = 'MyItem'; hook = 'onRightClickedInAir'; procedureName = 'MyProcedure' } } } | ConvertTo-Json -Depth 8 -Compress
Invoke-WebRequest http://127.0.0.1:39217/mcp -Method Post -Headers $headers -Body $body
```

```json
{"jsonrpc":"2.0","id":9,"result":{"content":[{"type":"text","text":"{\"itemName\":\"MyItem\",\"hook\":\"onRightClickedInAir\",\"procedureName\":\"MyProcedure\",\"attached\":true}"}]}}
```

Hook desconhecido é rejeitado sem alterar o item:

```json
{"jsonrpc":"2.0","id":10,"error":{"code":-32600,"message":"Unsupported item procedure hook 'doesNotExist'. Supported hooks: ..."}}
```

Uma procedure que exige, por exemplo, `damagesource` não pode ser ligada a `onRightClickedInAir`, pois aquele evento não fornece essa dependência:

```json
{"jsonrpc":"2.0","id":11,"error":{"code":-32600,"message":"Procedure requires dependencies not provided by hook 'onRightClickedInAir': damagesource:damagesource"}}
```

Após sucesso, a tool salva o item e o regenera. A regeneração recursiva de `ProcedureGUI` não é necessária nesse caso: ela é usada quando as dependências da própria procedure mudam; aqui apenas o item, que é o novo chamador, mudou e já foi regenerado.

## Testar o transporte stdio isolado

Compile as classes sem o entry point do plugin (ou use a saída da compilação Gradle) e inicie `net.mcreator.mcpbridge.McpStdioLauncher`. Envie uma solicitação JSON por linha, por exemplo:

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"manual-test","version":"1.0"}}}
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
```

O processo responderá uma linha JSON para cada request. Sem uma janela do MCreator aberta, uma chamada de tool retorna um erro; dentro do MCreator, ela usa o workspace ativo:

```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_workspace_info","arguments":{}}}
```

A resposta contém os metadados reais do workspace quando há uma janela MCreator aberta.

## Usar stdio dentro do MCreator

Por natureza, stdio liga um único cliente ao processo que o hospeda. O MCreator normalmente usa seu próprio stdin/stdout; portanto o servidor fica **desligado por padrão** para não consumir a entrada do aplicativo nem corromper sua saída. Para habilitá-lo, inicie a JVM do MCreator com:

```text
-Dmcreator.mcp.stdio.enabled=true
```

Isso só é apropriado se um launcher MCP controlar o processo do MCreator e reservar stdin/stdout para JSON-RPC. O transporte HTTP pode funcionar ao mesmo tempo e é a opção indicada para uma instância do MCreator já aberta.

## Integração com Workspace

`MCreatorToolBridge` recebe a janela ativa via `MCreatorLoadedEvent` e obtém o Workspace com `MCreator#getWorkspace()`. Todas as operações continuam encapsuladas em `onEdt(...)`, que usa `SwingUtilities.invokeLater` e aguarda com `CompletableFuture`, evitando que a thread MCP acesse Swing ou Workspace em paralelo.

Também confirme o evento de fechamento/descarregamento de plugin para chamar `MCreatorMcpBridgePlugin.shutdown()`; enquanto isso, há um shutdown hook da JVM como salvaguarda.
