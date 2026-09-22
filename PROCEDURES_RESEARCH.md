# Levantamento: procedures e Blockly no MCreator 2026.2

## Escopo e fonte examinada

Este levantamento foi feito no checkout configurado como `mcreator_path` para o plugin, no commit `c93379e1`:

`C:\Users\Slayer\Documents\Codex\2026-09-05\quero-criar-um-plugin-java-para\work\MCreatorCore`

Nenhuma tool MCP foi adicionada nesta fase e nenhum workspace foi alterado.

## Resultado curto

Uma procedure não é uma classe chamada `ProcedureBlock`. Ela é um `ModElement` de tipo `ModElementType.PROCEDURE`, cujo objeto persistido é `net.mcreator.element.types.Procedure`. A lógica Blockly fica no campo público `procedurexml`, como XML Blockly. As referências de eventos de outros elementos apontam para essa procedure por nome, usando `net.mcreator.element.parts.procedure.Procedure`.

Logo, é possível construir procedures programaticamente, mas uma tool de escrita precisa validar o XML com o compilador Blockly do core, validar dependências e só então persistir/regenerar. Escrever XML diretamente no arquivo `.mod.json` não é um caminho aceitável.

## Classes e pacotes confirmados

Os caminhos abaixo são relativos ao `mcreator_path` indicado acima.

| Caminho | Conteúdo relevante |
| --- | --- |
| `src/main/java/net/mcreator/element/types/Procedure.java` | Representação persistida da procedure. Declara `@BlocklyXML(name = "procedures", defaultXML = ...) public String procedurexml` e `skipDependencyNullCheck`. Converte XML em código/dados de template. |
| `src/main/java/net/mcreator/element/parts/procedure/Procedure.java` | Referência leve a uma procedure: guarda apenas o nome, resolve o ModElement no Workspace e fornece dependências/retorno. Seu adaptador Gson serializa a referência como string ou `null`. |
| `src/main/java/net/mcreator/ui/modgui/ProcedureGUI.java` | Editor visual da procedure. Carrega o Blockly, recompila alterações, mostra notas, salva `procedurexml` e regenera chamadores quando dependências mudam. |
| `src/main/java/net/mcreator/ui/procedure/AbstractProcedureSelector.java` | Base dos seletores de evento. Lista procedures, compara dependências exigidas com as fornecidas pelo evento e compara tipo de retorno. |
| `src/main/java/net/mcreator/ui/procedure/ProcedureSelector.java` | Seletor usado nos editores de Item, Block etc. A criação pela UI cria `new ModElement(..., ModElementType.PROCEDURE)` e abre `ProcedureGUI`. |
| `src/main/java/net/mcreator/ui/blockly/BlocklyPanel.java` | Painel Swing/JCEF. Mantém XML inicial, aplica XML no Blockly via JavaScript e lê XML atual com `getXML()`. |
| `src/main/java/net/mcreator/ui/blockly/BlocklyEditorType.java` | Define `BlocklyEditorType.PROCEDURE` como `("procedures", "ptpl", "event_trigger")`; o bloco raiz obrigatório é `event_trigger`. |
| `src/main/java/net/mcreator/blockly/BlocklyToCode.java` | Base que faz parse do XML com DOM, encontra o bloco inicial e acumula `BlocklyCompileNote`, dependências e código. XML inválido vira nota de erro. |
| `src/main/java/net/mcreator/blockly/java/BlocklyToProcedure.java` | Compilador específico de procedures. Lê o trigger externo no campo `trigger` do bloco raiz, variáveis locais e tipo de retorno. |
| `src/main/java/net/mcreator/blockly/data/ExternalTrigger.java` | Modelo dos triggers globais: id, APIs requeridas, dependências fornecidas, lado, possibilidade de cancelamento e resultado. |
| `src/main/java/net/mcreator/blockly/data/BlocklyLoader.java` e `src/main/java/net/mcreator/blockly/data/ExternalTriggerLoader.java` | Carregam blocos e triggers externos do Blockly. |
| `src/main/java/net/mcreator/blockly/java/blocks/` | Implementações Java dos blocos Blockly internos, incluindo chamadas de procedure e retorno. Não devem ser reproduzidas pelo plugin. |
| `src/main/java/net/mcreator/generator/blockly/BlocklyBlockCodeGenerator.java`, `OutputBlockCodeGenerator.java`, `ProceduralBlockCodeGenerator.java` | Geradores usados pelo compilador para transformar blocos em código de template. |
| `src/main/java/net/mcreator/workspace/elements/ModElementManager.java` | Persiste um `GeneratableElement` em `elements/<nome>.mod.json`; usa Gson e valida/corrige no carregamento. É explicitamente marcado como não thread-safe. |
| `src/test/java/net/mcreator/integration/generator/GTVariables.java` | Teste de integração confirmado: instancia `Procedure`, atribui `procedure.procedurexml`, adiciona o ModElement ao Workspace e chama `workspace.getGenerator().generateElement(...)`. |

Também existem recursos do editor em `src/main/resources/blockly/` e conversores históricos em `src/main/java/net/mcreator/element/converter/*Procedure*`. Os conversores são mais uma evidência de que o XML/versionamento de procedures exige cuidado.

## Representação e persistência

Uma procedure nova começa com o XML padrão declarado em `element/types/Procedure.java`:

```xml
<xml xmlns="https://developers.google.com/blockly/xml">
  <block type="event_trigger" deletable="false" x="40" y="40">
    <field name="trigger">no_ext_trigger</field>
  </block>
</xml>
```

`ProcedureGUI.getElementFromGUI()` cria `net.mcreator.element.types.Procedure`, copia `skipDependencyNullCheck` e atribui o retorno de `BlocklyPanel.getXML()` a `procedurexml`.

`ModElementManager.storeModElement(GeneratableElement)` grava esse objeto em `<workspace>/elements/<nome>.mod.json`. Durante geração, `element/types/Procedure.getAdditionalTemplateData()` cria `BlocklyToProcedure`, atualiza os metadados do ModElement com:

- `dependencies`: lista de `Dependency` encontrada no XML;
- `return_type`: tipo retornado, quando houver;
- código da procedure, código adicional, variáveis locais, blocos usados e código de trigger.

Portanto, o XML é a fonte de verdade; Java gerado é derivado dele.

## Como uma procedure é ligada a um evento

Os elementos geráveis têm campos fortemente tipados de `net.mcreator.element.parts.procedure.Procedure`. Exemplos confirmados:

- `element/types/Item.java`: `onRightClickedInAir`, `onRightClickedOnBlock`, `onCrafted`, `onEntityHitWith` e outros;
- `element/types/Block.java`: `onRightClicked`, `onBlockAdded`, `onNeighbourBlockChanges`, `onTickUpdate` e outros;
- `element/types/LivingEntity.java`: `whenMobDies`, `onRightClickedOn`, `onMobTickUpdate` e outros.

Esses campos serializam o nome da procedure. A UI faz a validação com `AbstractProcedureSelector`: a procedure só é aceita se as dependências calculadas forem um subconjunto das dependências fornecidas pelo evento e se o retorno for compatível. Por exemplo, alguns ganchos requerem uma procedure que retorne `ACTIONRESULTTYPE` ou lógica, enquanto outros aceitam procedure sem retorno.

Após uma alteração nas dependências de uma procedure, `ProcedureGUI.afterGeneratableElementGenerated()` chama `ReferencesFinder.searchModElementUsages(...)` e regenera chamadores não bloqueados. Uma tool futura que anexe ou altere procedure deve reproduzir a intenção desse fluxo, não apenas trocar uma string no JSON.

## XML construível programaticamente? Sim, com ressalvas

Sim. O XML Blockly é uma estrutura de dados que pode ser construída externamente. O core já fornece o caminho de compilação:

1. usar o bloco raiz `event_trigger` definido por `BlocklyEditorType.PROCEDURE`;
2. compilar com `BlocklyToProcedure` (ou, no caminho normal de geração, `element.types.Procedure.getBlocklyToProcedure(...)`);
3. rejeitar qualquer `BlocklyCompileNote` de tipo `ERROR`;
4. verificar trigger global, APIs exigidas, dependências e tipo de retorno;
5. somente então adicionar/atualizar o ModElement, persistir via `ModElementManager`, gerar e atualizar a UI.

`BlocklyPanel` oferece `setInitialXML(String)` para o editor, mas ele depende do JCEF/WebView e da carga assíncrona do Blockly. Ele é apropriado para integração de UI, não é necessário nem ideal para uma tool MCP headless. Para a tool, o compilador de XML é o limite de validação mais importante.

## Riscos de corrupção ou inconsistência

- XML malformado, sem o bloco raiz `event_trigger`, ou com tipos de bloco não suportados gera notas de compilação e pode produzir código inválido/incompleto.
- Uma procedure pode pedir dependências que o evento não fornece. A UI bloqueia esse caso; uma tool que ignore isso pode gerar chamadas com dependências nulas ou código que não compila.
- Um retorno incompatível com o gancho (por exemplo, procedure sem o retorno esperado) pode quebrar a geração.
- Alterar XML sem regerar elementos que a referenciam deixa imports/assinaturas/metadata defasados. O core tem lógica específica para regenerar chamadores.
- `ModElementManager` e diversos métodos de `ModElement` são declarados não thread-safe. Todas as operações de Workspace, cache, persistência e geração devem continuar dentro da EDT através do padrão `SwingUtilities.invokeLater` + `CompletableFuture` já usado pelo plugin.
- Elementos com código bloqueado não são regenerados: `Generator.generateElement(...)` retorna sem gerar quando `ModElement.isCodeLocked()` é verdadeiro. Uma tool não deve destravar ou sobrescrever código bloqueado implicitamente.

## Código Java gerado e alternativa menor

O core permite edição manual de código, mas não como um “gancho customizado” genérico e seguro dentro dos elementos visuais:

- `Generator.generateElement(...)` sobrescreve arquivos de elementos não bloqueados.
- `ModElement.setCodeLock(true)` impede gerações futuras desse elemento.
- `CustomElementGUI` cria o tipo `CODE`, gera seu arquivo inicial e o bloqueia imediatamente; esse é o mecanismo oficial de elemento de código customizado.

Assim, a menor evolução segura **não** é uma tool arbitrária de substituir Java gerado. A melhor primeira implementação é `validate_procedure_xml`: ela não altera o workspace, recebe XML e contexto de procedure, retorna notas de compilação, dependências, trigger e retorno inferidos. Depois de validada em workspaces reais, o próximo passo é `create_procedure` usando o XML padrão ou XML previamente validado. Só depois faz sentido anexá-la a um evento específico de `item` ou `block`.

Para leitura de código, uma futura tool pode listar e ler arquivos sob o diretório do workspace apenas após validar que o caminho fica dentro dele. Escrita deve exigir confirmação explícita e, para arquivos associados a um ModElement, verificar `isCodeLocked()` antes de alterar qualquer coisa.

## Recomendação para a próxima decisão

Proponho a sequência abaixo, ainda sem implementá-la:

1. `validate_procedure_xml` somente leitura/validação, sem gravar;
2. `create_procedure` com XML padrão ou XML que tenha passado na validação;
3. `attach_item_procedure` para um único gancho de item, com tabela fixa de ganchos e dependências confirmadas;
4. ferramentas de leitura de arquivos gerados, limitadas ao workspace;
5. escrita de código somente em arquivo explicitamente escolhido, com proteção de elemento bloqueado e confirmação no protocolo.
