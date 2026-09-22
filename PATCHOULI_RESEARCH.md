# Patchouli 1.21.x — levantamento para MCreator 2026.2.33518

Fontes consultadas: documentação oficial do Patchouli, em especial *Getting Started*, *1.20 Upgrade Guide*, e as referências de Book, Category e Entry JSON.

## Estrutura confirmada

Em Patchouli 1.20+ (logo, na linha 1.21.x), um livro distribuído por um mod precisa de duas árvores de recursos:

```text
src/main/resources/
├── data/<modid>/patchouli_books/<bookId>/book.json
└── assets/<modid>/patchouli_books/<bookId>/en_us/
    ├── categories/
    ├── entries/
    └── templates/
```

`book.json` precisa de `name` e `landing_text`; como é declarado em `data/`, também precisa de `use_resource_pack: true`. `creative_tab` é opcional. O conteúdo do livro (categorias, entradas e templates) é carregado de `assets/` quando `use_resource_pack` é verdadeiro.

O workspace `C:/Users/Slayer/MCreatorWorkspaces/microtech` confirma o padrão de recursos do gerador NeoForge: ele usa `src/main/resources/data/microtech/` e `src/main/resources/assets/microtech/`.

## Formatos posteriores

- Categoria: `name`, `description` e `icon` são obrigatórios.
- Entrada: `name`, `category`, `icon` e `pages` são obrigatórios. A categoria deve usar um ID qualificado, por exemplo `microtech:machines`.
- Página `patchouli:text`: exige `type: "patchouli:text"` e `text`.
- Página `patchouli:item`: é confirmada e exige `type: "patchouli:item"` e `item` (uma ItemStack String). Não será escrita antes da fase que a solicitar.

Nenhuma textura personalizada é necessária para texto ou ícones de itens. A árvore `assets/` é obrigatória mesmo nesse caso, pois é onde o conteúdo do livro é lido; texturas adicionais somente são necessárias ao usar imagens ou recursos visuais próprios.
