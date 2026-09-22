# Levantamento: GeckoLib Reborn e MCP Bridge — MCreator 2026.2.33518

## Escopo, pré-requisitos e limites

Este documento registra somente investigação. Nenhuma tool GeckoLib foi adicionada, nenhum `ModElement` foi criado e nenhum arquivo do workspace foi alterado.

O plugin de terceiro foi confirmado como instalado em:

`C:\Users\Slayer\.mcreator\plugins\GeckoLib_Reborn_Plugin.zip`

O MCreator estava em execução durante os testes e respondeu pelo MCP HTTP local. O `plugin.json` dentro do ZIP declara:

| Campo | Valor confirmado |
| --- | --- |
| id | `geckolib_reborn_plugin` |
| versão | `7.3.8` |
| versões MCreator suportadas | `2026002` |
| ponto de entrada Java | `net.nerdypuzzle.geckolib.Launcher` |
| suporte declarado | Forge 1.20.1 / GeckoLib 4.8.4; NeoForge 1.21.1 / GeckoLib 4.9.2; NeoForge 26.1.2 / GeckoLib 5.5.2 |

Como esse é um plugin de terceiro, estes achados valem para a cópia efetivamente instalada acima, em MCreator 2026.2.33518. Eles não constituem uma API estável para versões futuras.

## Fontes examinadas

1. Resposta real de `list_element_types` no MCP do MCreator em execução.
2. Respostas reais de `get_element_schema` para cada tipo GeckoLib encontrado.
3. Arquivo instalado `GeckoLib_Reborn_Plugin.zip`: manifesto, classes, definições de gerador NeoForge 1.21.1, descrições de ajuda e definições Blockly.
4. `javap -private` sobre as classes reais do ZIP instalado:
   - `net.nerdypuzzle.geckolib.element.types.AnimatedArmor`
   - `net.nerdypuzzle.geckolib.element.types.AnimatedBlock`
   - `net.nerdypuzzle.geckolib.element.types.AnimatedEntity`
   - `net.nerdypuzzle.geckolib.element.types.AnimatedItem`
   - `net.nerdypuzzle.geckolib.registry.PluginElementTypes`
5. README oficial do projeto GeckoLib Reborn, usado apenas para corroborar a versão de GeckoLib e a convenção de diretórios de assets por gerador.

## Resultado de `list_element_types` — resposta completa

```json
[
  "achievement", "animatedarmor", "animatedblock", "animatedentity", "animateditem",
  "armor", "armortrim", "attribute", "bannerpattern", "bebiome", "beblock",
  "beentity", "beitem", "bescript", "biome", "block", "code", "command",
  "curiosbauble", "curiosslot", "custombook", "damagetype", "dimension",
  "enchantment", "feature", "fluid", "function", "gamerule", "gui", "item",
  "itemextension", "keybind", "ldlibgui", "livingentity", "loottable", "overlay",
  "painting", "particle", "plant", "potion", "potioneffect", "procedure",
  "projectile", "recipe", "specialentity", "structure", "tab", "tool",
  "villagerprofession", "villagertrade"
]
```

Os quatro identificadores cuja origem GeckoLib foi confirmada pelo registro `PluginElementTypes` são:

- `animatedarmor`
- `animatedblock`
- `animatedentity`
- `animateditem`

Os demais tipos da resposta não foram atribuídos ao GeckoLib: alguns podem vir do core e outros de plugins instalados diferentes.

## Schemas reais e campos observados

`get_element_schema` devolveu um JSON de elemento vazio para todos os quatro tipos, com `_fv: 89`, `_type` correspondente e uma chave `definition`. Os itens abaixo enumeram integralmente os campos de `definition` retornados. Campo presente no schema não significa, por si só, que seja obrigatório: o plugin aplica validações na GUI e/ou durante a geração.

### `animatedarmor`

Campos retornados:

```text
enableHelmet, textureHelmet, enableBody, textureBody, enableLeggings,
textureLeggings, enableBoots, textureBoots, creativeTabs, armorTextureFile,
model, idle, head, chest, rightArm, leftArm, rightLeg, leftLeg, rightBoot,
leftBoot, helmetName, bodyName, leggingsName, bootsName, helmetSpecialInfo,
bodySpecialInfo, leggingsSpecialInfo, bootsSpecialInfo, helmetItemRenderType,
helmetItemCustomModelName, bodyItemRenderType, bodyItemCustomModelName,
leggingsItemRenderType, leggingsItemCustomModelName, bootsItemRenderType,
bootsItemCustomModelName, helmetImmuneToFire, bodyImmuneToFire,
leggingsImmuneToFire, bootsImmuneToFire, fullyEquipped, maxDamage,
damageValueHelmet, damageValueBody, damageValueLeggings, damageValueBoots,
enchantability, toughness, knockbackResistance, equipSound, repairItems
```

Confirmações binárias adicionais: a classe contém os campos `TextureHolder` para as quatro texturas e `String` para `armorTextureFile`, `model` e `idle`; também há quatro referências de procedure (`onHelmetTick`, `onBodyTick`, `onLeggingsTick`, `onBootsTick`) que não apareceram no schema vazio retornado pela tool. A ajuda embutida diz que `model` é o nome do modelo/da animação GeckoLib JSON e que `armorTextureFile` é a textura do modelo de armadura GeckoLib. Portanto são referências externas relevantes, mas o formato exato de valor e a validação feita pela GUI ainda precisam ser observados antes de automatizar escrita.

### `animatedblock`

Campos retornados:

```text
texture, textureTop, textureLeft, textureFront, textureRight, textureBack,
renderType, rotationMode, enablePitch, emissiveRendering, displayFluidOverlay,
animateBlockItem, itemTexture, particleTexture, tintType, isItemTinted,
hasTransparency, connectedSides, transparencyType, disableOffset, boundingBoxes,
name, specialInformation, hardness, resistance, hasGravity, isWaterloggable,
creativeTabs, destroyTool, customDrop, dropAmount, useLootTableForDrops,
requiresCorrectTool, enchantPowerBonus, plantsGrowOn, canRedstoneConnect,
lightOpacity, material, tickRate, tickRandomly, isReplaceable, canProvidePower,
emittedRedstonePower, colorOnMap, creativePickItem, offsetType, aiPathNodeType,
flammability, fireSpreadSpeed, isLadder, slipperiness, speedFactor, jumpFactor,
animationCount, reactionToPushing, isNotColidable, isCustomSoundType,
soundOnStep, breakSound, fallSound, hitSound, placeSound, stepSound, luminance,
unbreakable, breakHarvestLevel, hasInventory, guiBoundTo, openGUIOnRightClick,
inventorySize, inventoryStackSize, inventoryDropWhenDestroyed,
inventoryComparatorPower, generateFeature, inventoryOutSlotIDs,
inventoryInSlotIDs, hasEnergyStorage, energyInitial, energyCapacity,
energyMaxReceive, energyMaxExtract, isFluidTank, fluidCapacity,
fluidRestrictions, restrictionBiomes, blocksToReplace, generationShape,
frequencyPerChunks, frequencyOnChunk, minGenerateHeight, maxGenerateHeight,
normal, displaySettings, vanillaToolTier, blockstateList
```

A classe instalada confirma `TextureHolder` nos sete campos de textura; `animationCount` é `Number`; e inclui procedures de bloco e de geração. As definições NeoForge 1.21.1 geram um `BlockEntity`, um renderer e modelo de block entity, além dos JSONs de blockstate/modelo do bloco. A ajuda embutida define `animationCount` como a quantidade de animações de procedure que o bloco pode usar. O schema vazio não contém um campo inequívoco de arquivo `.geo.json`; por isso não se deve inferir que `normal` ou `displaySettings` aceitam um caminho de modelo sem inspecionar o fluxo da GUI.

### `animatedentity`

Campos retornados:

```text
mobName, mobLabel, mobModelTexture, mobModelGlowTexture, visualScale,
boundingBoxScale, entityDataEntries, modelWidth, modelHeight, modelShadowSize,
mountedYOffset, stepHeight, hasSpawnEgg, spawnEggBaseColor, spawnEggDotColor,
creativeTabs, isBoss, bossBarColor, bossBarType, equipmentMainHand,
equipmentOffHand, equipmentHelmet, equipmentBody, equipmentLeggings,
equipmentBoots, mobBehaviourType, mobCreatureType, attackStrength,
attackKnockback, knockbackResistance, movementSpeed, armorBaseValue,
trackingRange, followRange, health, xpAmount, waterMob, flyingMob, guiBoundTo,
inventorySize, inventoryStackSize, deathTime, lerp, disableCollisions, ridable,
canControlForward, canControlStrafe, immuneToFire, immuneToArrows,
immuneToFallDamage, immuneToCactus, immuneToDrowning, immuneToLightning,
immuneToPotions, immuneToPlayer, immuneToExplosion, immuneToTrident,
immuneToAnvil, immuneToWither, immuneToDragonBreath, mobDrop, livingSound,
hurtSound, deathSound, stepSound, raidCelebrationSound, hasAI, aiBase, aixml,
model, groupName, animation1, animation2, animation3, animation4, animation5,
animation6, animation7, animation8, animation9, animation10, enable2, enable3,
enable4, enable5, enable6, enable7, enable8, enable9, enable10,
animationControllers, breedable, tameable, disableDeathRotation, headMovement,
eyeHeight, breedTriggerItems, ranged, rangedAttackItem, rangedItemType,
rangedAttackInterval, rangedAttackRadius, height, attackRate, raidSpawnsCount,
spawnThisMob, doesDespawnWhenIdle, spawningProbability, mobSpawningType,
minNumberOfMobsPerGroup, maxNumberOfMobsPerGroup, restrictionBiomes,
spawnInDungeons
```

Confirmações binárias: `mobModelTexture`, `mobModelGlowTexture`, `model`, `groupName` e `animation1` a `animation10` são strings; `animationControllers` é uma lista de `AnimatedEntity.ControllerEntry`. Há procedures de entidade normais (por exemplo `whenMobDies`, `onRightClickedOn`, `onMobTickUpdate`) e métodos próprios de controladores (`getBaseControllers`, `getAdditiveControllers`, `getTransitionTicks`). Para NeoForge 1.21.1 o README oficial associa os assets GeckoLib às pastas `assets/<modid>/geo/` e `assets/<modid>/animations/`; a textura continua uma referência do recurso de textura do MCreator. Nomes/IDs das animações e formato interno de `ControllerEntry` requerem investigação adicional antes de qualquer criação automática.

### `animateditem`

Campos retornados:

```text
renderType, texture, name, idle, rarity, displaySettings, leftArm, rightArm,
creativeTabs, firstPersonArms, stackSize, enchantability, useDuration,
toolType, damageCount, recipeRemainder, destroyAnyBlock, immuneToFire,
stayInGridWhenCrafting, enableArmPose, damageOnCrafting, enableMeleeDamage,
damageVsEntity, specialInformation, hasGlow, guiBoundTo, inventorySize,
inventoryStackSize, isFood, nutritionalValue, saturation, eatResultItem,
isMeat, isAlwaysEdible, animation, normal, armPoseList, disableSwing
```

A classe instalada também revela o campo `customModelName`, `glowCondition` e os hooks de procedure de item, que não vieram no schema vazio. `texture` é um `TextureHolder`; `idle`, `animation`, `normal`, `displaySettings`, `leftArm` e `rightArm` são strings. As definições NeoForge 1.21.1 incluem renderer e modelo de item próprios e adicionam utilitário de animação de primeira pessoa quando `firstPersonArms` está ativo. Não há evidência suficiente para tratar `animation` ou `normal` como caminho de arquivo; a criação deve ser precedida pela inspeção de uma instância criada pela UI e de seus assets.

## Referências externas e diretórios de assets confirmados

O README oficial do GeckoLib Reborn especifica, para o gerador que o workspace ativo usa (NeoForge 1.21.1 / GeckoLib 4.9.2):

- modelos GeckoLib JSON em `assets/<modid>/geo/`;
- animações GeckoLib JSON em `assets/<modid>/animations/`.

Para NeoForge 26.1.2 a convenção muda para `assets/<modid>/geckolib/models/` e `assets/<modid>/geckolib/animations/`. Isso torna perigoso reutilizar automaticamente caminhos de uma versão de gerador em outra.

Além desses arquivos, os schemas referenciam recursos geridos pelo próprio MCreator: `TextureHolder` (texturas), sons, itens/blocos, abas criativas, biomas e procedures. O plugin contém os templates de geração NeoForge 1.21.1 em `neoforge-1.21.1/templates/` e suas definições em `neoforge-1.21.1/animated*.definition.yaml`, confirmando que a geração é mais ampla que apenas escrever um `.mod.json`.

## Blockly customizado GeckoLib

O ZIP instalado contém o grupo `procedures/$geckolib.json` e os seguintes blocos:

```text
block_animation, get_block_animation, get_entity_animation, get_entity_texture,
get_item_animation, is_geckolib_entity, item_animation, no_animation,
play_newgeckoanim, set_armor_animation, set_entity_texture,
stop_armor_animation, stop_block_animation, stop_item_animation, stop_procedure
```

Exemplos de assinaturas confirmadas nas definições JSON:

| Bloco | Forma / saída | Dependências confirmadas |
| --- | --- | --- |
| `block_animation` | statement; `x`, `y`, `z`, `value` numéricos | `world` |
| `stop_block_animation` | statement; `x`, `y`, `z` numéricos | `world` |
| `get_block_animation` | saída `Number`; `x`, `y`, `z` numéricos | `world` |
| `play_newgeckoanim` | statement; entidade, animação e controller | seleção de `animatedentity` |
| `get_entity_animation` | saída `String`; entidade e controller | seleção de `animatedentity` |
| `get_item_animation` | saída `String`; item | seleção de `animateditem` |
| `item_animation` / `stop_item_animation` | statement; item e animação quando aplicável | seleção de `animateditem` |
| `set_armor_animation` / `stop_armor_animation` | statement; item e animação quando aplicável | seleção de `animatedarmor` |
| `set_entity_texture` / `get_entity_texture` | statement/saída `String`; entidade e textura quando aplicável | seleção de `animatedentity` |
| `is_geckolib_entity` | saída `Boolean`; entidade | — |
| `no_animation` | saída `String` | — |

Teste runtime realizado: foi enviado a `validate_procedure_xml` um XML com raiz `event_trigger`, trigger `no_ext_trigger` e um bloco `block_animation` com quatro números. A resposta real foi `valid: true`, lista de notas vazia, dependência `world` e `return_type: null`. Isso confirma que o registro Blockly do GeckoLib está ativo no processo, e não apenas presente no ZIP.

## Riscos e conclusão para uma futura tool de criação

1. Os tipos estão registrados e o MCP consegue obter schemas reais, mas não foi validado o fluxo de criação persistente de nenhum deles.
2. Cada tipo possui classes, GUIs, templates e recursos externos próprios. `create_mod_element` usado para tipos do core não deve ser aplicado por analogia.
3. A presença de defaults vazios para modelo/animação/texturas não prova que eles são aceitos na geração. Criar e gerar um elemento com assets incompletos pode deixar o workspace em estado inválido ou produzir código que não compila.
4. O modelo de assets varia por gerador (especialmente NeoForge 1.21.1 versus 26.1.2). Uma tool deve consultar o gerador do workspace e validar caminhos relativos contidos no workspace antes de escrever.
5. Controladores de entidade, `blockstateList`, `armPoseList` e referências a procedures têm tipos internos específicos. A serialização de valores improvisados pode divergir do Gson/adaptadores usados pelo plugin de terceiro.
6. O autor descreve o desenvolvimento do plugin como desacelerado severamente. Qualquer integração MCP deve declarar explicitamente compatibilidade best-effort e detectar a presença/versão do plugin em runtime.

### Próximo passo seguro proposto

Antes de criar qualquer element GeckoLib via MCP, criar manualmente pela UI **um exemplar descartável de cada tipo** no mesmo workspace NeoForge 1.21.1, com assets mínimos válidos. Em seguida, comparar os `.mod.json` produzidos, os arquivos de asset e o código gerado. Essa observação fornece valores reais de campos que o schema vazio não consegue distinguir como obrigatórios/opcionais e permite desenhar uma tool limitada por tipo, sem supor que a API do core se aplica ao plugin de terceiro.

## Confirmação prática — `Solarenergy` (`animatedblock`)

Após este levantamento, foi encontrado no workspace real `microtech` um bloco GeckoLib criado e salvo manualmente pela UI:

`C:\Users\Slayer\MCreatorWorkspaces\microtech\elements\Solarenergy.mod.json`

Ele declara `_fv: 89`, `_type: "animatedblock"` e foi lido diretamente do disco, sem criação ou edição por MCP. A comparação foi feita contra uma nova resposta de `get_element_schema` para `animatedblock`, obtida do MCP em execução.

### Diferenças em relação ao schema recém-criado

Dos campos de `definition`, somente os quatro abaixo diferem do schema inicial retornado pela tool:

| Campo | Schema novo | Valor salvo pela UI | Interpretação confirmada |
| --- | --- | --- | --- |
| `texture` | `""` | `"solar_energy"` | Referência ao recurso de textura sem extensão e sem caminho. |
| `name` | `"Mcp Schema Template"` | `"Solarenergy"` | Nome exibido do bloco. |
| `normal` | `""` | `"solar_energy.geo.json"` | Nome do arquivo de modelo GeckoLib, sem diretório. |
| `displaySettings` | `""` | `"solar_energy_display.json"` | Nome do arquivo de configurações de exibição, sem diretório. |

Todos os demais campos presentes no schema inicial permaneceram nos valores padrão: por exemplo, `animationCount: 1`, `boundingBoxes` com cubo de 16×16×16, `hardness: 1.0`, `resistance: 10.0`, `renderType: 0`, coleções vazias e as opções de inventário/energia/worldgen desativadas. Para este elemento, isso é evidência prática de defaults salvos pelo editor; não prova que todos são opcionais em qualquer configuração.

### Assets físicos e relação com o `.mod.json`

Foram encontrados os arquivos abaixo no workspace:

```text
src/main/resources/assets/microtech/geo/entity/solar_energy.geo.json
src/main/resources/assets/microtech/textures/block/solar_energy.png
src/main/resources/assets/microtech/blockstates/solarenergy.json
src/main/resources/assets/microtech/models/item/solarenergy.json
```

Não existe arquivo sob `src/main/resources/assets/microtech/animations/` relacionado a este elemento.

O modelo em `geo/entity/solar_energy.geo.json` é JSON GeckoLib/Bedrock com `format_version: "1.12.0"` e `minecraft:geometry`. O valor `normal: "solar_energy.geo.json"` corresponde ao nome-base desse arquivo, **não** ao caminho completo `geo/entity/...`. A textura `solar_energy.png` também é referenciada como nome-base (`texture: "solar_energy"`), não como caminho e sem extensão.

Os dois outros arquivos são gerados pelo MCreator/gerador: o blockstate referencia `microtech:custom/solarenergy_particle`, e o modelo de item usa `microtech:displaysettings/solar_energy_display` e a textura `microtech:block/solar_energy`. Eles demonstram que `displaySettings` é uma referência de nome usada pela geração, mesmo que o arquivo correspondente não esteja entre os assets pesquisados pelo nome `solar_energy_display` no momento da inspeção.

### Grau de confiança após o exemplo real

Agora há confiança **média**, não alta, para propor uma futura tool limitada a `animatedblock` sem animações: sabemos o tipo real, o formato persistido, os quatro valores não-default e a convenção de referência de modelo/textura do exemplo. Ainda não é seguro implementá-la porque faltam confirmações fundamentais:

1. o fluxo exato pelo qual a GUI importa/cria o arquivo `.geo.json` e seu `displaySettings` associado;
2. a validação de existência/compatibilidade que a GUI executa antes de salvar e gerar;
3. o formato de `blockstateList` e de animações de procedure quando usados;
4. o comportamento de geração com um modelo ou textura ausente;
5. uma comparação com ao menos um bloco animado de fato, incluindo um arquivo em `animations/`.

Assim, a recomendação continua sendo **não implementar `create_geckolib_block` ainda**. O próximo levantamento mínimo é criar pela UI um `animatedblock` com uma animação funcional e, idealmente, um `animateditem`; então comparar seus arquivos persistidos e assets antes de decidir a superfície e validações de uma tool MCP.

## Segunda confirmação prática — `Storage` (`animatedblock` com animação)

Foi então criado e salvo manualmente pela UI um segundo elemento:

`C:\Users\Slayer\MCreatorWorkspaces\microtech\elements\Storage.mod.json`

Como no primeiro caso, a leitura foi direta do arquivo físico e nenhuma criação/alteração foi feita pelo MCP. O elemento usa:

```json
{
  "_type": "animatedblock",
  "definition": {
    "texture": "solar_energy",
    "animateBlockItem": true,
    "name": "Storage",
    "animationCount": 1,
    "normal": "storage.geo.json",
    "displaySettings": "solar_energy_display.json"
  }
}
```

### Comparação `Storage` × `Solarenergy`

Os dois elementos têm a mesma estrutura e os mesmos defaults. As únicas diferenças na `definition` são:

| Campo | `Solarenergy` | `Storage` |
| --- | --- | --- |
| `name` | `Solarenergy` | `Storage` |
| `normal` | `solar_energy.geo.json` | `storage.geo.json` |
| `animateBlockItem` | `false` | `true` |

`animationCount` continua `1` nos dois elementos. Crucialmente, **não há campo de animação no `.mod.json` de `Storage`**: nem `animation`, nem um caminho para `storage.animation.json`. Portanto não existe referência cruzada explícita JSON→animação; o `normal` apenas identifica o modelo.

### Asset de animação e convenção realmente usada

O arquivo de animação criado manualmente está em:

`src/main/resources/assets/microtech/animations/entity/storage.animation.json`

Ele é JSON GeckoLib com:

```json
{
  "format_version": "1.8.0",
  "animations": {
    "idle": {
      "loop": true,
      "animation_length": 10
    }
  }
}
```

O modelo correspondente é `assets/microtech/geo/entity/storage.geo.json`; ambos compartilham a base `storage`, mas isso não é gravado como uma relação no ModElement.

O código Java gerado pelo próprio MCreator para `StorageBlockModel` confirmou como essa associação funciona em runtime. Ele calcula recursos candidatos a partir da base `storage`:

```java
// modelos candidatos
microtech:geo/block/storage.geo.json
microtech:geo/entity/storage.geo.json
microtech:geo/storage.geo.json

// animações candidatas
microtech:animations/block/storage.animation.json
microtech:animations/entity/storage.animation.json
microtech:animations/storage.animation.json
```

Ele consulta `GeckoLibCache` e usa a primeira variante carregada, na ordem block → entity → flat, para modelo e animação de maneira independente. Assim, no exemplo real a resolução é para `geo/entity/storage.geo.json` e `animations/entity/storage.animation.json`. O primeiro exemplo (`Solarenergy`) gera a mesma lógica de procura, porém nenhum asset de animação com a base `solar_energy` existe.

### Avaliação atualizada

Há confiança **alta para propor**, mas ainda não para implementar sem uma validação adicional, uma `create_geckolib_block` restrita a assets que já existam no workspace:

- entrada mínima proposta: nome, textura base, nome de modelo `*.geo.json` e opção `animateBlockItem`;
- animação não deve ser um campo persistido da tool: ela é descoberta pelo gerador/GeckoLib pelo mesmo nome-base do modelo, com sufixo `.animation.json` e um dos três diretórios acima;
- a tool deve validar antes de persistir que o modelo existe em um layout aceito; se for exigida animação, deve exigir `animations/{block|entity}/<base>.animation.json` ou `animations/<base>.animation.json` existente e JSON válido;
- a tool não deve importar, copiar, renomear nem gerar modelos/animações nesta primeira versão.

Ainda falta uma confirmação antes de implementação: o caminho Java exato para instanciar e persistir o `ModElementType` de terceiro (`animatedblock`) sem abrir/sujar a GUI, e a validação equivalente da GUI para assets ausentes. Isso precisa ser confirmado contra as classes reais do GeckoLib Reborn antes de conectar qualquer criação à tool MCP.

## Investigação da criação Java sem GUI — decisão: não implementar ainda

Esta investigação foi feita contra o core local MCreator 2026.2.33518 e o ZIP GeckoLib Reborn efetivamente instalado. Nenhuma tool foi adicionada e nenhum elemento foi criado.

### Caminho genérico que o core usa para um novo elemento

O core não possui uma factory pública equivalente a `ModElementType.newInstance(ModElement)`. O fluxo confirmado em `net.mcreator.ui.dialogs.NewModElementDialog` é:

1. criar `new ModElement(workspace, name, type)`;
2. chamar `type.getModElementGUI(mcreator, modElement, false)`;
3. abrir a GUI; e, no momento de salvar, `ModElementGUI.getElementFromGUI()` devolve o `GeneratableElement`.

Depois disso, o ciclo de persistência é genérico e não depende do tipo:

```java
workspace.addModElement(modElement);
workspace.markDirty();
workspace.getModElementManager().storeModElement(element);
workspace.getGenerator().generateBase();
workspace.getGenerator().generateElement(element);
workspace.getModElementManager().storeModElementPicture(element);
```

Esse mesmo ciclo é usado pelo MCP Bridge para `item` e `block`, e corresponde ao método `finishModCreation` em `ModElementGUI` no core 2026.2.33518.

### Registro GeckoLib confirmado

O bytecode de `net.nerdypuzzle.geckolib.registry.PluginElementTypes.load()` confirma que o plugin registra:

```java
new ModElementType<>("animatedblock", 'D', AnimatedBlockGUI::new, AnimatedBlock.class)
```

Logo, `ModElementTypeLoader.getModElementType("animatedblock")` localiza corretamente o tipo no runtime, fornece sua `AnimatedBlockGUI` e expõe `AnimatedBlock.class` por `getModElementStorageClass()`.

`AnimatedBlockGUI.getElementFromGUI()` cria diretamente `new AnimatedBlock(modElement)` e preenche seus campos, incluindo `texture`, `animateBlockItem`, `normal` e `displaySettings`. A GUI também declara validadores próprios para `geoModel` e `displaySettings`; portanto, ignorá-la elimina validações específicas do plugin de terceiro.

### Por que não há rota segura sem GUI e sem reflection

`AnimatedBlock` possui construtor público `(ModElement)` e campos públicos, sem setters públicos dedicados. Porém, o MCP Bridge não compila contra o ZIP GeckoLib Reborn: ele é opcional em runtime. Consequentemente, para chamar esse construtor e gravar `texture`, `normal` e `animateBlockItem` diretamente sem usar a GUI, o bridge teria de usar reflection sobre:

```text
net.nerdypuzzle.geckolib.element.types.AnimatedBlock
```

Isso é frágil: uma alteração de nome de classe, construtor ou campo na próxima versão do GeckoLib Reborn faria a tool falhar em runtime. Adicionar o ZIP do plugin de terceiro como dependência `compileOnly` não resolve o requisito de opcionalidade; ele ainda cria acoplamento binário e risco de `NoSuchMethodError`/`NoSuchFieldError` quando a versão instalada divergir.

Existe uma alternativa genérica interna: `ModElementManager.fromJSONtoGeneratableElement(...)` resolve dinamicamente `animatedblock` no registro e desserializa a `definition` pela classe de storage. Ela evita reflection **no código do MCP Bridge**, mas exige um `.mod.json` completo e válido. A única forma confirmada de obter defaults completos no core é justamente a `ModElementGUI` invisível usada hoje por `get_element_schema`; montar esse JSON manualmente ou a partir de um template estático omitiria as validações GeckoLib e ficaria igualmente vulnerável a mudanças de versão.

### Decisão

Pela regra explícita desta tarefa, `create_geckolib_block` **não será implementada agora**: para uma criação sem GUI, a única rota direta confirmada exige reflection contra código de terceiro, e a rota de JSON sem reflection ainda dependeria da GUI invisível para defaults/validação.

Uma decisão futura pode escolher conscientemente uma destas abordagens:

1. aceitar reflection best-effort, com verificação estrita de versão/campos e mensagens de erro claras;
2. permitir a GUI invisível do GeckoLib como fonte de defaults e usar o desserializador genérico do core para aplicar somente os quatro campos controlados;
3. obter do autor do GeckoLib Reborn uma API pública de criação/headless estável.

Até existir essa decisão, o MCP Bridge deve continuar apenas lendo schemas e observando elementos GeckoLib já criados manualmente.

## Viabilidade da GUI invisível — decisão: inviável sem reflection

Foi investigada a alternativa de usar `AnimatedBlockGUI` somente em memória, no EDT, como o MCP Bridge já faz em `get_element_schema`.

### O que foi confirmado como funcionando

O runtime já executa, sem mostrar aba/janela, este fluxo para `get_element_schema`:

```java
ModElement temporary = new ModElement(workspace, "McpSchemaTemplate", type);
ModElementGUI<?> gui = type.getModElementGUI(mcreator, temporary, false);
GeneratableElement defaults = gui.getElementFromGUI();
```

Isso constrói a `AnimatedBlockGUI` dentro da EDT e retorna um `AnimatedBlock` default. Não houve necessidade de JCEF/WebView para esse tipo de GUI; a classe usa componentes Swing usuais. Logo, a **construção invisível** em si é limpa e já foi demonstrada pelo schema dinâmico.

### O que o schema não faz

`get_element_schema` chama somente `getElementFromGUI()`. Ele não chama o fluxo de salvar de `ModElementGUI` e, portanto, não executa as validações do formulário.

No core, o fluxo de salvar primeiro consulta os resultados de validação das páginas e só então entra no método privado `finishModCreation`. Dentro dele, `getAdditionalValidationResult(element)` roda antes de persistir. Assim, extrair defaults não equivale a simular um usuário preenchendo e clicando em Salvar.

### Barreiras para preencher/validar sem reflection

O bytecode da `AnimatedBlockGUI` instalada confirma que os únicos controles relevantes são privados:

```text
private TextureSelectionButton texture;
private final JCheckBox animateBlockItem;
private final VComboBox<String> geoModel;
private final VComboBox<String> displaySettings;
```

Não há setters públicos para esses controles, nem método público que aceite `texture`, `normal`/`geoModel` e `animateBlockItem` como dados. O único método público de extração é `getElementFromGUI()`, que somente lê o estado interno atual. O método do core que reúne a validação de páginas e persiste (`finishModCreation`) é privado; `getAdditionalValidationResult` é protegido; e a GUI não expõe uma API pública headless para acionar o mesmo fluxo sem também mostrar a interface.

`AnimatedBlockGUI.openInEditingMode(AnimatedBlock)` é público na classe concreta, mas exige justamente uma instância de `AnimatedBlock` já preenchida. Para obter essa instância sem depender da GUI seria necessário reflection, dependência binária opcional, ou desserialização de JSON completo já conhecida.

### Teste isolado e conclusão

O teste isolado disponível foi a construção invisível já exercida por `get_element_schema`: ela retorna defaults sem janela, sem tab e sem erro de EDT/JCEF. Não foi possível realizar o segundo passo — preencher os três controles e disparar validação — por API pública. Forçar esse passo por acesso reflexivo a campos privados deixaria de testar a alternativa “GUI invisível sem reflection” e introduziria a mesma fragilidade que esta alternativa pretendia evitar.

Portanto, a GUI invisível é adequada para **leitura de defaults/schema**, mas não é uma rota pública completa para criação programática do `animatedblock`. `create_geckolib_block` continua não implementada até haver uma decisão explícita de aceitar reflection best-effort ou de manter suporte somente leitura.

## Decisão

`create_geckolib_block` não será implementada no MCP Bridge. A criação programática segura exigiria manipular campos privados da `AnimatedBlockGUI` (`texture`, `geoModel`, `animateBlockItem` e `displaySettings`) ou acessar diretamente a classe de terceiro `AnimatedBlock`; ambas as alternativas requerem reflection sobre estado interno do GeckoLib Reborn.

Esse acoplamento é incompatível com a manutenção instável/de ritmo reduzido do plugin de terceiro: uma atualização pode mudar nomes, tipos ou ciclo de validação sem qualquer compatibilidade binária. O bridge manterá apenas leitura e diagnóstico de assets GeckoLib. Esta decisão poderá ser reaberta somente se uma versão futura do GeckoLib Reborn expuser uma API pública e estável para criar/preencher/validar esses elementos sem reflection.
