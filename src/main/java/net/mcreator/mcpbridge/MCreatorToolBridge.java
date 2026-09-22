package net.mcreator.mcpbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.blockly.BlocklyCompileNote;
import net.mcreator.blockly.IBlockGenerator;
import net.mcreator.blockly.InternalBlocksLoader;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.blockly.data.Dependency;
import net.mcreator.blockly.java.BlocklyToProcedure;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.parts.procedure.Procedure;
import net.mcreator.element.types.CustomElement;
import net.mcreator.generator.GeneratorTemplate;
import net.mcreator.element.types.Item;
import net.mcreator.java.JavaConventions;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.VariableType;
import net.mcreator.workspace.elements.VariableTypeLoader;
import net.mcreator.workspace.elements.VariableElement;
import net.mcreator.workspace.settings.WorkspaceSettings;

import javax.swing.SwingUtilities;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Boundary between the MCP protocol threads and the current MCreator workspace. */
final class MCreatorToolBridge implements ToolBridge {
    private volatile MCreator mcreator;

    /** Called for every opened MCreator window by the plugin's MCreatorLoadedEvent listener. */
    void setMCreator(MCreator mcreator) {
        this.mcreator = mcreator;
    }

    @Override public Object listModElements(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            String requestedType = optionalString(arguments.get("elementType"));
            List<Object> elements = new ArrayList<>();
            for (ModElement element : workspace.getModElements()) {
                if (requestedType == null || element.getType().getRegistryName().equalsIgnoreCase(requestedType)) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", element.getName());
                    entry.put("type", element.getType().getRegistryName());
                    entry.put("registryName", element.getRegistryName());
                    entry.put("isCodeLocked", element.isCodeLocked());
                    elements.add(entry);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("elements", elements);
            result.put("count", elements.size());
            result.put("filteredBy", requestedType);
            return result;
        });
    }

    @Override public Object listWorkspaceVariables(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            List<Object> variables = new ArrayList<>();
            for (VariableElement variable : currentWorkspace().getVariableElements()) variables.add(variableResponse(variable));
            return Map.of("variables", variables, "count", variables.size());
        });
    }

    @Override public Object createWorkspaceVariable(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            String name = requiredString(arguments, "name");
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("name must be a valid Java-style variable name");
            if (workspace.getVariableElementByName(name) != null) throw new IllegalArgumentException("A variable named '" + name + "' already exists");
            VariableElement variable = new VariableElement(name);
            applyVariableFields(workspace, variable, arguments, true);
            workspace.addVariableElement(variable);
            requireMCreator().reloadWorkspaceTabContents();
            return variableResponse(variable);
        });
    }

    @Override public Object updateWorkspaceVariable(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            VariableElement variable = requireVariable(workspace, requiredString(arguments, "name"));
            applyVariableFields(workspace, variable, arguments, false);
            workspace.markDirty();
            requireMCreator().reloadWorkspaceTabContents();
            return variableResponse(variable);
        });
    }

    @Override public Object deleteWorkspaceVariable(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            VariableElement variable = requireVariable(workspace, requiredString(arguments, "name"));
            workspace.removeVariableElement(variable);
            requireMCreator().reloadWorkspaceTabContents();
            return Map.of("name", variable.getName(), "deleted", true);
        });
    }

    private static VariableElement requireVariable(Workspace workspace, String name) {
        VariableElement variable = workspace.getVariableElementByName(name);
        if (variable == null) throw new IllegalArgumentException("No workspace variable named '" + name + "' exists");
        return variable;
    }

    private static void applyVariableFields(Workspace workspace, VariableElement variable, Map<String, Object> arguments, boolean creating) {
        Object suppliedType = arguments.get("type");
        if (creating && suppliedType == null) throw new IllegalArgumentException("type is required");
        if (suppliedType != null) {
            if (!(suppliedType instanceof String typeName)) throw new IllegalArgumentException("type must be a string");
            VariableType type = VariableTypeLoader.INSTANCE.fromName(typeName);
            if (type == null || !type.canBeGlobal(workspace.getGeneratorConfiguration()) || !type.isSupportedInWorkspace(workspace))
                throw new IllegalArgumentException("Unsupported global variable type '" + typeName + "'");
            variable.setType(type);
        }
        VariableType type = variable.getType();
        if (type == null) throw new IllegalArgumentException("Variable type is unavailable");
        Object suppliedScope = arguments.get("scope");
        if (creating && suppliedScope == null) suppliedScope = "GLOBAL_SESSION";
        if (suppliedScope != null) {
            if (!(suppliedScope instanceof String scopeName)) throw new IllegalArgumentException("scope must be a string");
            VariableType.Scope scope;
            try { scope = VariableType.Scope.valueOf(scopeName.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unknown variable scope '" + scopeName + "'"); }
            if (scope == VariableType.Scope.LOCAL || !List.of(type.getSupportedScopesWithoutLocal(workspace.getGeneratorConfiguration())).contains(scope))
                throw new IllegalArgumentException("Scope '" + scope + "' is not supported for type '" + type.getName() + "'");
            variable.setScope(scope);
        }
        if (arguments.containsKey("value")) variable.setValue(arguments.get("value"));
        else if (creating) variable.setValue(type.getDefaultValue(workspace));
    }

    private static Map<String, Object> variableResponse(VariableElement variable) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", variable.getName());
        result.put("type", variable.getTypeString());
        result.put("scope", variable.getScope().name());
        result.put("value", variable.getValue());
        return result;
    }

    @Override public Object createItem(Map<String, Object> arguments) throws Exception {
        Map<String, Object> itemArguments = new LinkedHashMap<>(arguments);
        itemArguments.put("elementType", "item");
        return createModElement(itemArguments);
    }

    /**
     * Creates MCreator's native, code-locked {@code code} element and replaces only its generated
     * Java file with the caller-provided complete source. This mirrors CustomElementGUI's creation
     * order: generate the template first, lock the element, then add it to the workspace.
     */
    @Override public Object createCodeElement(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            String elementName = JavaConventions.convertToValidClassName(requiredString(arguments, "name"));
            if (elementName == null || elementName.isBlank()) {
                throw new IllegalArgumentException("name does not contain a valid code element name");
            }
            if (workspace.containsModElement(elementName)) {
                throw new IllegalArgumentException("A mod element named '" + elementName + "' already exists");
            }

            ModElement modElement = new ModElement(workspace, elementName, ModElementType.CODE);
            CustomElement element = new CustomElement(modElement);
            Path sourceFile = codeElementSourceFile(workspace, element);
            workspace.getGenerator().generateElement(element);
            Files.writeString(sourceFile, requiredString(arguments, "source"), StandardCharsets.UTF_8);
            modElement.setCodeLock(true);
            workspace.addModElement(modElement);
            workspace.markDirty();
            requireMCreator().reloadWorkspaceTabContents();

            Map<String, Object> result = new LinkedHashMap<>(creationResult(modElement));
            result.put("sourcePath", sourceFile.toString());
            result.put("codeLocked", true);
            return result;
        });
    }

    /** Reads the sole Java file managed by an existing native {@code code} element. */
    @Override public Object readCodeElement(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            ModElement modElement = requireCodeElement(workspace, requiredString(arguments, "name"));
            Path sourceFile = codeElementSourceFile(workspace, (CustomElement) modElement.getGeneratableElement());
            if (!Files.isRegularFile(sourceFile)) {
                throw new IllegalStateException("Code source file is missing for '" + modElement.getName() + "': " + sourceFile);
            }
            return Map.of("name", modElement.getName(), "sourcePath", sourceFile.toString(),
                    "source", Files.readString(sourceFile, StandardCharsets.UTF_8));
        });
    }

    /** Replaces source only for an existing native {@code code} element, never a generated mod element. */
    @Override public Object writeCodeElement(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            ModElement modElement = requireCodeElement(workspace, requiredString(arguments, "name"));
            Path sourceFile = codeElementSourceFile(workspace, (CustomElement) modElement.getGeneratableElement());
            Files.writeString(sourceFile, requiredString(arguments, "source"), StandardCharsets.UTF_8);
            workspace.markDirty();
            return Map.of("name", modElement.getName(), "sourcePath", sourceFile.toString(), "updated", true);
        });
    }

    private static ModElement requireCodeElement(Workspace workspace, String name) {
        ModElement modElement = workspace.getModElementByName(name);
        if (modElement == null || modElement.getType() != ModElementType.CODE
                || !modElement.isCodeLocked() || !(modElement.getGeneratableElement() instanceof CustomElement)) {
            throw new IllegalArgumentException("No native code element named '" + name + "' exists");
        }
        return modElement;
    }

    /** CustomElementGUI uses the first code template; reject unexpected multi-file layouts rather than writing broadly. */
    private static Path codeElementSourceFile(Workspace workspace, CustomElement element) {
        List<GeneratorTemplate> templates = workspace.getGenerator().getModElementGeneratorTemplatesList(element);
        if (templates.size() != 1) {
            throw new IllegalStateException("The active generator exposes " + templates.size()
                    + " files for a code element; MCP Bridge only supports the single-file CustomElement layout");
        }
        Path sourceFile = templates.getFirst().getFile().toPath().toAbsolutePath().normalize();
        Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
        if (!sourceFile.startsWith(workspaceRoot)) {
            throw new IllegalStateException("MCreator resolved the code element source outside the current workspace");
        }
        return sourceFile;
    }

    @Override public Object createModElement(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            String suppliedName = requiredString(arguments, "name");
            String elementName = JavaConventions.convertToValidClassName(suppliedName);
            if (elementName == null || elementName.isBlank()) throw new IllegalArgumentException("name does not contain a valid mod element name");
            if (workspace.containsModElement(elementName)) throw new IllegalArgumentException("A mod element named '" + elementName + "' already exists");

            ModElementType<?> elementType = supportedCreationType(requiredString(arguments, "elementType"));
            ModElement modElement = new ModElement(workspace, elementName, elementType);
            ModElementGUI<?> elementGui = elementType.getModElementGUI(requireMCreator(), modElement, false);
            if (elementGui == null) throw new IllegalStateException("The active generator does not support '" + elementType.getRegistryName() + "' elements");
            GeneratableElement element = elementGui.getElementFromGUI();
            if (element == null) throw new IllegalStateException("MCreator did not create a default '" + elementType.getRegistryName() + "' element");

            // This is the same persistence/generation lifecycle used when a ModElementGUI is saved.
            workspace.addModElement(modElement);
            workspace.markDirty();
            workspace.getModElementManager().storeModElement(element);
            workspace.getGenerator().generateBase();
            workspace.getGenerator().generateElement(element);
            workspace.getModElementManager().storeModElementPicture(element);
            modElement.reloadElementIcon();
            modElement.reinit(workspace);
            // Matches the 2026.2.33518 ModElementGUI save lifecycle.  The later
            // Generator.refreshWorkspaceSourceInfo API is not present in that release.
            requireMCreator().reloadWorkspaceTabContents();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", modElement.getName());
            result.put("type", modElement.getType().getRegistryName());
            result.put("registryName", modElement.getRegistryName());
            result.put("created", true);
            return result;
        });
    }

    /** Lists types dynamically from the MCreator registry rather than maintaining a bridge-side list. */
    @Override public Object listElementTypes(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            List<String> types = new ArrayList<>();
            for (ModElementType<?> elementType : ModElementTypeLoader.getAllModElementTypes()) {
                types.add(elementType.getRegistryName());
            }
            Collections.sort(types);
            return Map.of("elementTypes", types);
        });
    }

    /**
     * Builds the default editor state for a registered type using MCreator's own new-element GUI
     * provider. The temporary ModElement is never added to the Workspace, stored, generated, or
     * shown. ModElementManager supplies the same configured Gson serialization used for .mod.json.
     */
    @Override public Object getElementSchema(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            String requestedType = requiredString(arguments, "element_type").toLowerCase(Locale.ROOT);
            ModElementType<?> elementType;
            try {
                elementType = ModElementTypeLoader.getModElementType(requestedType);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Unknown MCreator element type '" + requestedType + "'");
            }

            Workspace workspace = currentWorkspace();
            ModElement temporaryModElement = new ModElement(workspace, "McpSchemaTemplate", elementType);
            ModElementGUI<?> elementGui = elementType.getModElementGUI(requireMCreator(), temporaryModElement, false);
            if (elementGui == null) {
                throw new IllegalStateException("The active generator does not support element type '"
                        + elementType.getRegistryName() + "'");
            }

            GeneratableElement defaultElement = elementGui.getElementFromGUI();
            if (defaultElement == null) {
                throw new IllegalStateException("MCreator did not create a default element for type '"
                        + elementType.getRegistryName() + "'");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("elementType", elementType.getRegistryName());
            result.put("mimeType", "application/json");
            result.put("schema", workspace.getModElementManager().generatableElementToJSON(defaultElement));
            return result;
        });
    }

    /**
     * Creates any registered, JSON-persisted MCreator element from a patch over MCreator's own
     * default definition. This intentionally has no bridge-side list of fields: Gson plus
     * GEValidator remain the source of truth for core and third-party storage classes.
     */
    @Override public Object createElementFromJson(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            ModElementType<?> type = requireJsonPersistedElementType(requiredString(arguments, "elementType"));
            String name = JavaConventions.convertToValidClassName(requiredString(arguments, "name"));
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name does not contain a valid mod element name");
            if (workspace.containsModElement(name)) throw new IllegalArgumentException("A mod element named '" + name + "' already exists");

            ModElement modElement = new ModElement(workspace, name, type);
            GeneratableElement defaultElement = defaultElementFromGui(workspace, type, modElement);
            GeneratableElement configured = deserializeDefinitionPatch(workspace, modElement, defaultElement,
                    requiredJsonObject(arguments, "definition"));
            persistAndGenerate(workspace, modElement, configured);

            Map<String, Object> result = new LinkedHashMap<>(creationResult(modElement));
            result.put("definition", Json.parse(workspace.getModElementManager().generatableElementToJSON(configured)));
            return result;
        });
    }

    /** Replaces only supplied JSON fields on an existing persisted ModElement, then validates and regenerates it. */
    @Override public Object updateElementFromJson(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            ModElement modElement = workspace.getModElementByName(requiredString(arguments, "name"));
            if (modElement == null) throw new IllegalArgumentException("No mod element named '" + requiredString(arguments, "name") + "' exists");
            requireJsonPersistedElementType(modElement.getType().getRegistryName());
            GeneratableElement existing = modElement.getGeneratableElement();
            if (existing == null) throw new IllegalStateException("MCreator could not load the persisted definition of '" + modElement.getName() + "'");
            GeneratableElement configured = deserializeDefinitionPatch(workspace, modElement, existing,
                    requiredJsonObject(arguments, "definition"));
            persistAndGenerate(workspace, modElement, configured);

            return Map.of("name", modElement.getName(), "type", modElement.getType().getRegistryName(),
                    "updated", true, "definition", Json.parse(workspace.getModElementManager().generatableElementToJSON(configured)));
        });
    }

    /** Returns the exact Gson envelope that MCreator would persist as elements/<name>.mod.json. */
    @Override public Object getElementDefinition(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            ModElement modElement = workspace.getModElementByName(requiredString(arguments, "name"));
            if (modElement == null) throw new IllegalArgumentException("No mod element named '" + requiredString(arguments, "name") + "' exists");
            if (modElement.getType() == ModElementType.CODE) {
                throw new IllegalArgumentException("Code elements have no .mod.json definition; use read_code_element instead");
            }
            GeneratableElement element = modElement.getGeneratableElement();
            if (element == null) throw new IllegalStateException("MCreator could not load the persisted definition of '" + modElement.getName() + "'");
            return Map.of("name", modElement.getName(), "type", modElement.getType().getRegistryName(),
                    "definition", Json.parse(workspace.getModElementManager().generatableElementToJSON(element)));
        });
    }

    private GeneratableElement defaultElementFromGui(Workspace workspace, ModElementType<?> type, ModElement modElement) {
        ModElementGUI<?> gui = type.getModElementGUI(requireMCreator(), modElement, false);
        if (gui == null) throw new IllegalStateException("The active generator does not support '" + type.getRegistryName() + "' elements");
        GeneratableElement element = gui.getElementFromGUI();
        if (element == null) throw new IllegalStateException("MCreator did not create a default '" + type.getRegistryName() + "' element");
        return element;
    }

    private static ModElementType<?> requireJsonPersistedElementType(String typeName) {
        ModElementType<?> type;
        try { type = ModElementTypeLoader.getModElementType(typeName.toLowerCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unknown MCreator element type '" + typeName + "'"); }
        if (type == null || type == ModElementType.CODE || type == ModElementType.UNKNOWN) {
            throw new IllegalArgumentException("Element type '" + typeName + "' does not have a JSON-persisted definition supported by this API");
        }
        return type;
    }

    private static JsonObject requiredJsonObject(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(key + " must be a JSON object");
        JsonElement json = JsonParser.parseString(Json.stringify(value));
        if (!json.isJsonObject()) throw new IllegalArgumentException(key + " must be a JSON object");
        return json.getAsJsonObject();
    }

    private static GeneratableElement deserializeDefinitionPatch(Workspace workspace, ModElement modElement,
                                                                  GeneratableElement base, JsonObject patch) throws Exception {
        JsonObject envelope = JsonParser.parseString(workspace.getModElementManager().generatableElementToJSON(base)).getAsJsonObject();
        JsonObject definition = envelope.getAsJsonObject("definition");
        mergeJsonObject(definition, patch);
        GeneratableElement result = workspace.getModElementManager().fromJSONtoGeneratableElement(envelope.toString(), modElement, null);
        if (result == null) throw new IllegalArgumentException("MCreator rejected the supplied definition for '" + modElement.getType().getRegistryName() + "'");
        return result;
    }

    /** Recursive object merge: scalars, nulls, and arrays replace their existing value; nested objects merge. */
    private static void mergeJsonObject(JsonObject target, JsonObject patch) {
        for (Map.Entry<String, JsonElement> entry : patch.entrySet()) {
            JsonElement current = target.get(entry.getKey());
            if (current != null && current.isJsonObject() && entry.getValue().isJsonObject()) {
                mergeJsonObject(current.getAsJsonObject(), entry.getValue().getAsJsonObject());
            } else {
                target.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
    }

    /** Lists files the user has already imported into src/main/resources; it never imports, writes, or deletes assets. */
    @Override public Object listWorkspaceAssets(Map<String, Object> arguments) throws Exception {
        Path root = onEdt(() -> workspaceAssetsRoot(currentWorkspace()));
        if (!Files.isDirectory(root)) return Map.of("assets", List.of(), "count", 0);
        List<Object> assets;
        try (Stream<Path> paths = Files.walk(root)) {
            assets = paths.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).limit(5000)
                    .<Object>map(path -> assetDescription(root, path)).toList();
        }
        return Map.of("assets", assets, "count", assets.size(), "root", root.toString());
    }

    /** Reads text metadata/assets (JSON, mcmeta, lang, txt) only; binary texture files stay discoverable but unread. */
    @Override public Object readWorkspaceAsset(Map<String, Object> arguments) throws Exception {
        String relativePath = requiredString(arguments, "path");
        Path file = onEdt(() -> resolveWorkspaceAsset(currentWorkspace(), relativePath));
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(lowerName.endsWith(".json") || lowerName.endsWith(".mcmeta") || lowerName.endsWith(".lang") || lowerName.endsWith(".txt"))) {
            throw new IllegalArgumentException("Only text assets (.json, .mcmeta, .lang, .txt) may be read; the asset remains listed but binary files are not returned");
        }
        return Map.of("path", relativePath.replace('\\', '/'), "text", Files.readString(file, StandardCharsets.UTF_8), "mimeType", assetMimeType(file));
    }

    private static Path workspaceAssetsRoot(Workspace workspace) {
        return workspace.getWorkspaceFolder().toPath().resolve("src/main/resources").toAbsolutePath().normalize();
    }

    private static Path resolveWorkspaceAsset(Workspace workspace, String relativePath) {
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) throw new IllegalArgumentException("path must be relative to src/main/resources");
        Path root = workspaceAssetsRoot(workspace);
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IllegalArgumentException("No workspace asset exists at '" + relativePath + "'");
        return file;
    }

    private static Map<String, Object> assetDescription(Path root, Path file) {
        Path relative = root.relativize(file);
        return Map.of("path", relative.toString().replace('\\', '/'), "bytes", file.toFile().length(), "mimeType", assetMimeType(file));
    }

    private static String assetMimeType(Path file) {
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".json") || lowerName.endsWith(".mcmeta")) return "application/json";
        if (lowerName.endsWith(".png")) return "image/png";
        return "text/plain";
    }

    /**
     * Read-only diagnostic for the three asset layouts emitted by GeckoLib Reborn's generated
     * AnimatedBlock model class. This does not instantiate a third-party GUI or alter Workspace state.
     */
    @Override public Object checkGeckolibBlockAssets(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            String modelName = requiredString(arguments, "modelName");
            validateGeckolibAssetBaseName(modelName);

            boolean geckoLibAvailable;
            try {
                geckoLibAvailable = ModElementTypeLoader.getModElementType("animatedblock") != null;
            } catch (IllegalArgumentException ignored) {
                geckoLibAvailable = false;
            }

            Path assetsRoot = workspace.getWorkspaceFolder().toPath().resolve("src/main/resources/assets")
                    .resolve(workspace.getWorkspaceSettings().getModID()).normalize();
            List<Path> modelCandidates = List.of(
                    assetsRoot.resolve("geo/block").resolve(modelName + ".geo.json"),
                    assetsRoot.resolve("geo/entity").resolve(modelName + ".geo.json"),
                    assetsRoot.resolve("geo").resolve(modelName + ".geo.json"));
            List<Path> animationCandidates = List.of(
                    assetsRoot.resolve("animations/block").resolve(modelName + ".animation.json"),
                    assetsRoot.resolve("animations/entity").resolve(modelName + ".animation.json"),
                    assetsRoot.resolve("animations").resolve(modelName + ".animation.json"));
            Path textureCandidate = assetsRoot.resolve("textures/block").resolve(modelName + ".png");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("geckoLibAvailable", geckoLibAvailable);
            result.put("modelName", modelName);
            result.put("model", geckolibAssetResult(modelCandidates));
            result.put("texture", singleGeckolibAssetResult(textureCandidate));
            result.put("animation", geckolibAssetResult(animationCandidates));
            return result;
        });
    }

    /** Best-effort Curios 1.5 integration. The storage class is intentionally resolved only at runtime. */
    @Override public Object createCuriosSlot(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            requireWorkspaceDependency(workspace, "curios_api");
            String elementName = newUniqueElementName(workspace, requiredString(arguments, "name"));
            String textureName = requiredString(arguments, "textureName");
            validateCuriosName(textureName, "textureName");
            ModElementType<?> type = requireThirdPartyType("curiosslot");
            ModElement modElement = new ModElement(workspace, elementName, type);
            GeneratableElement element = instantiateThirdPartyElement(type, modElement);
            setThirdPartyField(element, "texture", textureName + ".png");
            setThirdPartyField(element, "name", optionalString(arguments.get("slotName")) == null ? elementName : requiredString(arguments, "slotName"));
            setThirdPartyField(element, "amount", optionalPositiveInt(arguments.get("amount"), 1, "amount"));
            setThirdPartyField(element, "modelToggling", optionalBoolean(arguments.get("modelToggling"), false, "modelToggling"));
            boolean changeOrder = optionalBoolean(arguments.get("changeOrder"), false, "changeOrder");
            setThirdPartyField(element, "changeOrder", changeOrder);
            setThirdPartyField(element, "slotOrder", optionalNonNegativeInt(arguments.get("slotOrder"), 0, "slotOrder"));
            persistAndGenerate(workspace, modElement, element);
            return creationResult(modElement);
        });
    }

    /** Creates the non-model Curios bauble variant. Custom Java models and procedure hooks remain UI-only. */
    @Override public Object createCuriosBauble(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            requireWorkspaceDependency(workspace, "curios_api");
            String elementName = newUniqueElementName(workspace, requiredString(arguments, "name"));
            String itemName = requiredString(arguments, "itemName");
            ModElement item = workspace.getModElementByName(itemName);
            if (item == null || !"item".equals(item.getType().getRegistryName()))
                throw new IllegalArgumentException("itemName must name an existing item ModElement");
            String suppliedSlotType = requiredString(arguments, "slotType");
            String upperSlotType = suppliedSlotType.toUpperCase(Locale.ROOT);
            String slotType;
            if (CURIO_DEFAULT_SLOTS.contains(upperSlotType)) {
                slotType = upperSlotType;
            } else {
                ModElement customSlot = workspace.getModElementByName(suppliedSlotType);
                if (customSlot == null || !"curiosslot".equals(customSlot.getType().getRegistryName()))
                    throw new IllegalArgumentException("slotType must be a built-in Curios slot or the exact name of an existing curiosslot");
                slotType = customSlot.getName();
            }
            ensureItemIsNotCuriosBauble(workspace, itemName);
            ModElementType<?> type = requireThirdPartyType("curiosbauble");
            ModElement modElement = new ModElement(workspace, elementName, type);
            GeneratableElement element = instantiateThirdPartyElement(type, modElement);
            setThirdPartyField(element, "item", itemName);
            setThirdPartyField(element, "slotType", slotType);
            setThirdPartyField(element, "slotAmount", optionalPositiveInt(arguments.get("slotAmount"), 1, "slotAmount"));
            setThirdPartyField(element, "addSlot", optionalBoolean(arguments.get("addSlot"), false, "addSlot"));
            setThirdPartyField(element, "enderMask", optionalBoolean(arguments.get("enderMask"), false, "enderMask"));
            setThirdPartyField(element, "friendlyPigs", optionalBoolean(arguments.get("friendlyPigs"), false, "friendlyPigs"));
            setThirdPartyField(element, "snowWalk", optionalBoolean(arguments.get("snowWalk"), false, "snowWalk"));
            setThirdPartyField(element, "hasModel", false);
            persistAndGenerate(workspace, modElement, element);
            return creationResult(modElement);
        });
    }

    private static final Set<String> CURIO_DEFAULT_SLOTS = Set.of("HEAD", "NECKLACE", "BACK", "BODY", "BRACELET", "HANDS", "RING", "BELT", "CHARM", "CURIO");

    private static String newUniqueElementName(Workspace workspace, String suppliedName) {
        String name = JavaConventions.convertToValidClassName(suppliedName);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name does not contain a valid mod element name");
        if (workspace.containsModElement(name)) throw new IllegalArgumentException("A mod element named '" + name + "' already exists");
        return name;
    }

    private static void requireWorkspaceDependency(Workspace workspace, String dependency) {
        if (!workspace.getWorkspaceSettings().getDependencies().contains(dependency))
            throw new IllegalStateException("The workspace must enable the '" + dependency + "' API before creating Curios elements");
    }

    private static ModElementType<?> requireThirdPartyType(String registryName) {
        try {
            ModElementType<?> type = ModElementTypeLoader.getModElementType(registryName);
            if (type == null) throw new IllegalArgumentException("not registered");
            return type;
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Curios plugin type '" + registryName + "' is unavailable; install and enable Nerdy's Curios API Plugin");
        }
    }

    private static GeneratableElement instantiateThirdPartyElement(ModElementType<?> type, ModElement modElement) {
        try {
            Constructor<?> constructor = type.getModElementStorageClass().getConstructor(ModElement.class);
            Object value = constructor.newInstance(modElement);
            if (!(value instanceof GeneratableElement element)) throw new IllegalStateException("Curios storage class is not a GeneratableElement");
            return element;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Curios plugin API changed: cannot instantiate '" + type.getRegistryName() + "'", error);
        }
    }

    private static void setThirdPartyField(GeneratableElement element, String fieldName, Object value) {
        try {
            Field field = element.getClass().getField(fieldName);
            field.set(element, value);
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            throw new IllegalStateException("Curios plugin API changed: cannot set field '" + fieldName + "'", error);
        }
    }

    private void ensureItemIsNotCuriosBauble(Workspace workspace, String itemName) {
        for (ModElement modElement : workspace.getModElements()) {
            if (!"curiosbauble".equals(modElement.getType().getRegistryName())) continue;
            GeneratableElement element = modElement.getGeneratableElement();
            try {
                Object assignedItem = element.getClass().getField("item").get(element);
                if (itemName.equals(assignedItem)) throw new IllegalArgumentException("The item '" + itemName + "' is already assigned to a Curios bauble");
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Curios plugin API changed: cannot inspect existing curiosbauble", error);
            }
        }
    }

    private void persistAndGenerate(Workspace workspace, ModElement modElement, GeneratableElement element) {
        workspace.addModElement(modElement);
        workspace.markDirty();
        workspace.getModElementManager().storeModElement(element);
        workspace.getGenerator().generateBase();
        workspace.getGenerator().generateElement(element);
        workspace.getModElementManager().storeModElementPicture(element);
        modElement.reloadElementIcon();
        modElement.reinit(workspace);
        requireMCreator().reloadWorkspaceTabContents();
    }

    private static Map<String, Object> creationResult(ModElement modElement) {
        return Map.of("name", modElement.getName(), "type", modElement.getType().getRegistryName(), "registryName", modElement.getRegistryName(), "created", true);
    }

    private static void validateCuriosName(String value, String parameter) {
        if (!value.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException(parameter + " must use only letters, digits, underscores, or hyphens");
    }

    private static int optionalPositiveInt(Object value, int defaultValue, String parameter) {
        int number = optionalInt(value, defaultValue, parameter);
        if (number < 1) throw new IllegalArgumentException(parameter + " must be at least 1");
        return number;
    }

    private static int optionalNonNegativeInt(Object value, int defaultValue, String parameter) {
        int number = optionalInt(value, defaultValue, parameter);
        if (number < 0) throw new IllegalArgumentException(parameter + " must not be negative");
        return number;
    }

    private static int optionalInt(Object value, int defaultValue, String parameter) {
        if (value == null) return defaultValue;
        if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) throw new IllegalArgumentException(parameter + " must be an integer");
        return number.intValue();
    }

    private static boolean optionalBoolean(Object value, boolean defaultValue, String parameter) {
        if (value == null) return defaultValue;
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException(parameter + " must be boolean");
        return flag;
    }

    private static Map<String, Object> geckolibAssetResult(List<Path> candidates) {
        List<Object> checked = new ArrayList<>();
        Path selected = null;
        for (Path candidate : candidates) {
            boolean exists = Files.isRegularFile(candidate);
            if (selected == null && exists) selected = candidate;
            checked.add(Map.of("path", candidate.toAbsolutePath().toString(), "exists", exists));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", selected != null);
        result.put("selectedPath", selected == null ? null : selected.toAbsolutePath().toString());
        result.put("candidates", checked);
        return result;
    }

    private static Map<String, Object> singleGeckolibAssetResult(Path candidate) {
        boolean exists = Files.isRegularFile(candidate);
        return Map.of("found", exists, "path", candidate.toAbsolutePath().toString());
    }

    private static void validateGeckolibAssetBaseName(String modelName) {
        if (!modelName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("modelName must be a file base name using only letters, digits, underscores, or hyphens");
        }
    }

    /**
     * Creates the two Patchouli 1.21.x resource trees required for a mod-owned book:
     * data/.../book.json declares the book and assets/... holds its localised content.
     */
    @Override public Object createPatchouliBook(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            String bookId = requiredString(arguments, "bookId");
            String name = requiredString(arguments, "name");
            validatePatchouliId(bookId, "bookId");

            Workspace workspace = currentWorkspace();
            PatchouliBookPaths bookPaths = patchouliBookPaths(workspace, bookId);

            if (Files.exists(bookPaths.dataBookDirectory()) || Files.exists(bookPaths.assetsBookDirectory())) {
                throw new IllegalArgumentException("Patchouli book '" + bookId + "' already exists");
            }

            Map<String, Object> bookJson = new LinkedHashMap<>();
            bookJson.put("name", name);
            bookJson.put("landing_text", "Welcome to " + name + ".");
            bookJson.put("use_resource_pack", true);

            Files.createDirectories(bookPaths.contentDirectory().resolve("categories"));
            Files.createDirectories(bookPaths.contentDirectory().resolve("entries"));
            Files.createDirectories(bookPaths.contentDirectory().resolve("templates"));
            Files.createDirectories(bookPaths.dataBookDirectory());
            Files.writeString(bookPaths.bookFile(), Json.stringify(bookJson), StandardCharsets.UTF_8);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bookId", bookId);
            result.put("modId", bookPaths.modId());
            result.put("bookFile", bookPaths.bookFile().toAbsolutePath().toString());
            result.put("contentDirectory", bookPaths.contentDirectory().toAbsolutePath().toString());
            result.put("created", true);
            return result;
        });
    }

    /** Creates a category with the required Patchouli icon, using minecraft:book as the safe default. */
    @Override public Object createPatchouliCategory(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            String bookId = requiredString(arguments, "bookId");
            String categoryId = requiredString(arguments, "categoryId");
            String name = requiredString(arguments, "name");
            String description = requiredString(arguments, "description");
            validatePatchouliId(bookId, "bookId");
            validatePatchouliId(categoryId, "categoryId");

            PatchouliBookPaths bookPaths = requirePatchouliBook(currentWorkspace(), bookId);
            Path categoryFile = bookPaths.contentDirectory().resolve("categories").resolve(categoryId + ".json");
            if (Files.exists(categoryFile)) {
                throw new IllegalArgumentException("Patchouli category '" + categoryId + "' already exists in book '" + bookId + "'");
            }

            Map<String, Object> categoryJson = new LinkedHashMap<>();
            categoryJson.put("name", name);
            categoryJson.put("description", description);
            categoryJson.put("icon", "minecraft:book");
            Files.createDirectories(categoryFile.getParent());
            Files.writeString(categoryFile, Json.stringify(categoryJson), StandardCharsets.UTF_8);

            return Map.of("bookId", bookId, "categoryId", categoryId,
                    "categoryFile", categoryFile.toAbsolutePath().toString(), "created", true);
        });
    }

    /** Creates only basic patchouli:text pages; item-page support remains intentionally out of scope. */
    @Override public Object createPatchouliEntry(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            String bookId = requiredString(arguments, "bookId");
            String categoryId = requiredString(arguments, "categoryId");
            String entryId = requiredString(arguments, "entryId");
            String name = requiredString(arguments, "name");
            String icon = requiredString(arguments, "icon");
            List<String> texts = requiredStringList(arguments, "texts");
            validatePatchouliId(bookId, "bookId");
            validatePatchouliId(categoryId, "categoryId");
            validatePatchouliId(entryId, "entryId");

            Workspace workspace = currentWorkspace();
            PatchouliBookPaths bookPaths = requirePatchouliBook(workspace, bookId);
            Path categoryFile = bookPaths.contentDirectory().resolve("categories").resolve(categoryId + ".json");
            if (!Files.isRegularFile(categoryFile)) {
                throw new IllegalArgumentException("Patchouli category '" + categoryId + "' does not exist in book '" + bookId + "'");
            }
            requireWorkspaceItemIcon(workspace, icon);

            Path entryFile = bookPaths.contentDirectory().resolve("entries").resolve(categoryId).resolve(entryId + ".json");
            if (Files.exists(entryFile)) {
                throw new IllegalArgumentException("Patchouli entry '" + entryId + "' already exists in category '" + categoryId + "'");
            }

            List<Object> pages = new ArrayList<>();
            for (String text : texts) {
                pages.add(Map.of("type", "patchouli:text", "text", text));
            }
            Map<String, Object> entryJson = new LinkedHashMap<>();
            entryJson.put("name", name);
            entryJson.put("icon", icon);
            entryJson.put("category", bookPaths.modId() + ":" + categoryId);
            entryJson.put("pages", pages);
            Files.createDirectories(entryFile.getParent());
            Files.writeString(entryFile, Json.stringify(entryJson), StandardCharsets.UTF_8);

            return Map.of("bookId", bookId, "categoryId", categoryId, "entryId", entryId,
                    "entryFile", entryFile.toAbsolutePath().toString(), "created", true);
        });
    }

    private static PatchouliBookPaths requirePatchouliBook(Workspace workspace, String bookId) {
        PatchouliBookPaths paths = patchouliBookPaths(workspace, bookId);
        if (!Files.isDirectory(paths.contentDirectory())) {
            throw new IllegalArgumentException("Patchouli book '" + bookId + "' does not exist in the current workspace");
        }
        return paths;
    }

    private static PatchouliBookPaths patchouliBookPaths(Workspace workspace, String bookId) {
        String modId = workspace.getWorkspaceSettings().getModID();
        Path resourcesRoot = workspace.getWorkspaceFolder().toPath().resolve("src/main/resources");
        Path dataBookDirectory = resourcesRoot.resolve("data").resolve(modId)
                .resolve("patchouli_books").resolve(bookId).normalize();
        Path assetsBookDirectory = resourcesRoot.resolve("assets").resolve(modId)
                .resolve("patchouli_books").resolve(bookId).normalize();
        return new PatchouliBookPaths(modId, dataBookDirectory, assetsBookDirectory,
                dataBookDirectory.resolve("book.json"), assetsBookDirectory.resolve("en_us"));
    }

    private static void validatePatchouliId(String value, String parameterName) {
        if (!value.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(parameterName + " must contain only lowercase letters, digits, and underscores");
        }
    }

    private static List<String> requiredStringList(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof List<?> rawValues) || rawValues.isEmpty()) {
            throw new IllegalArgumentException(key + " must be a non-empty array of strings");
        }
        List<String> values = new ArrayList<>();
        for (Object rawValue : rawValues) {
            if (!(rawValue instanceof String text)) {
                throw new IllegalArgumentException(key + " must contain only strings");
            }
            values.add(text);
        }
        return values;
    }

    private static void requireWorkspaceItemIcon(Workspace workspace, String icon) {
        String namespace = workspace.getWorkspaceSettings().getModID() + ":";
        for (ModElement element : workspace.getModElements()) {
            if (element.getType() == ModElementType.ITEM && icon.equals(namespace + element.getRegistryName())) {
                return;
            }
        }
        throw new IllegalArgumentException("icon must name an existing item in the current workspace, for example '"
                + namespace + "my_item': " + icon);
    }

    private record PatchouliBookPaths(String modId, Path dataBookDirectory, Path assetsBookDirectory,
                                      Path bookFile, Path contentDirectory) { }

    /**
     * Compiles Blockly XML against the active workspace's generator definitions without adding,
     * storing, or generating a mod element. The temporary ModElement exists only in memory.
     */
    @Override public Object validateProcedureXml(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> validateProcedureXml(currentWorkspace(), requiredString(arguments, "xml")));
    }

    /** Package-visible to allow an isolated HTTP test harness to use the exact validation logic. */
    static Object validateProcedureXml(Workspace workspace, String xml) {
        return validateProcedure(workspace, xml).toResponse();
    }

    /**
     * Validates first, then follows the procedure persistence order used by GTVariables:
     * add to the workspace, generate, and store. No ModElement is created before validation passes.
     */
    @Override public Object createProcedure(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Object result = createProcedure(currentWorkspace(), requiredString(arguments, "name"), requiredString(arguments, "xml"));
            requireMCreator().reloadWorkspaceTabContents();
            return result;
        });
    }

    /** Package-visible so the isolated HTTP integration test exercises the exact creation workflow. */
    static Object createProcedure(Workspace workspace, String suppliedName, String xml) throws Exception {
        ProcedureValidation validation = validateProcedure(workspace, xml);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Procedure XML validation failed: " + Json.stringify(validation.notes()));
        }

        String elementName = JavaConventions.convertToValidClassName(suppliedName);
        if (elementName == null || elementName.isBlank()) {
            throw new IllegalArgumentException("name does not contain a valid mod element name");
        }
        if (workspace.containsModElement(elementName)) {
            throw new IllegalArgumentException("A mod element named '" + elementName + "' already exists");
        }

        ModElement modElement = new ModElement(workspace, elementName, ModElementType.PROCEDURE);
        net.mcreator.element.types.Procedure procedure = new net.mcreator.element.types.Procedure(modElement);
        procedure.procedurexml = xml;

        // Exact persistence/generation order used by GTVariables for a Procedure.
        workspace.addModElement(modElement);
        workspace.getGenerator().generateElement(procedure, true);
        workspace.getModElementManager().storeModElement(procedure);
        modElement.reinit(workspace);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", modElement.getName());
        result.put("type", modElement.getType().getRegistryName());
        result.put("registryName", modElement.getRegistryName());
        result.put("created", true);
        validation.addInference(result);
        return result;
    }

    /**
     * Appends MCreator's built-in {@code java_code} Blockly block to a procedure's root chain.
     * This is deliberately append-only: callers cannot target arbitrary XML nodes or overwrite
     * existing Blockly structure. The Java text is emitted verbatim by JavaCodeProceduralBlock.
     */
    @Override public Object appendProcedureJavaSnippet(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            String procedureName = requiredString(arguments, "procedureName");
            String code = requiredString(arguments, "code");
            ModElement procedureModElement = workspace.getModElementByName(procedureName);
            if (procedureModElement == null || procedureModElement.getType() != ModElementType.PROCEDURE
                    || !(procedureModElement.getGeneratableElement() instanceof net.mcreator.element.types.Procedure procedure)) {
                throw new IllegalArgumentException("No procedure mod element named '" + procedureName + "' exists");
            }

            String updatedXml = appendJavaCodeBlock(procedure.procedurexml, code);
            ProcedureValidation validation = validateProcedure(workspace, updatedXml);
            if (!validation.valid()) {
                throw new IllegalArgumentException("Procedure XML validation failed after adding snippet: "
                        + Json.stringify(validation.notes()));
            }

            String originalXml = procedure.procedurexml;
            procedure.procedurexml = updatedXml;
            try {
                workspace.getGenerator().generateElement(procedure, true);
                workspace.getModElementManager().storeModElement(procedure);
                procedureModElement.reinit(workspace);
            } catch (Exception error) {
                procedure.procedurexml = originalXml;
                throw error;
            }
            requireMCreator().reloadWorkspaceTabContents();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", procedureModElement.getName());
            result.put("snippetAppended", true);
            validation.addInference(result);
            return result;
        });
    }

    private static String appendJavaCodeBlock(String procedureXml, String code) {
        Document document = parseBlocklyXml(procedureXml);
        Element rootBlock = null;
        for (Node node = document.getDocumentElement().getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element candidate && "block".equals(candidate.getTagName())
                    && "event_trigger".equals(candidate.getAttribute("type"))) {
                rootBlock = candidate;
                break;
            }
        }
        if (rootBlock == null) {
            throw new IllegalArgumentException("Procedure XML must have an event_trigger root block before adding a Java snippet");
        }

        Element tail = rootBlock;
        while (true) {
            Element next = directChild(tail, "next");
            Element nextBlock = next == null ? null : directChild(next, "block");
            if (nextBlock == null) break;
            tail = nextBlock;
        }
        Element next = document.createElement("next");
        Element snippetBlock = document.createElement("block");
        snippetBlock.setAttribute("type", "java_code");
        Element field = document.createElement("field");
        field.setAttribute("name", "CODE");
        field.setTextContent(code);
        snippetBlock.appendChild(field);
        next.appendChild(snippetBlock);
        tail.appendChild(next);
        return serializeBlocklyXml(document);
    }

    private static Element directChild(Element parent, String tagName) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && tagName.equals(child.getTagName())) return child;
        }
        return null;
    }

    private static String serializeBlocklyXml(Document document) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Could not serialize Blockly XML after adding Java snippet", error);
        }
    }

    /**
     * Attaches only to the public Procedure-typed fields declared directly by Item. Compatibility is
     * the same condition used by AbstractProcedureSelector.refreshList: every dependency required by
     * the procedure must be provided by the hook, and a non-null expected return type must match.
     */
    @Override public Object attachItemProcedure(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Object result = attachItemProcedure(currentWorkspace(), requiredString(arguments, "itemName"),
                    requiredString(arguments, "hook"), requiredString(arguments, "procedureName"));
            requireMCreator().reloadWorkspaceTabContents();
            return result;
        });
    }

    /** Package-visible so the isolated HTTP integration test exercises the exact attachment workflow. */
    static Object attachItemProcedure(Workspace workspace, String itemName, String hookName, String procedureName)
            throws Exception {
            ModElement itemModElement = workspace.getModElementByName(itemName);
            if (itemModElement == null || itemModElement.getType() != ModElementType.ITEM
                    || !(itemModElement.getGeneratableElement() instanceof Item item)) {
                throw new IllegalArgumentException("No item mod element named '" + itemName + "' exists");
            }

            ItemProcedureHook hook = itemProcedureHooks().get(hookName);
            if (hook == null) {
                throw new IllegalArgumentException("Unsupported item procedure hook '" + hookName
                        + "'. Supported hooks: " + String.join(", ", itemProcedureHooks().keySet()));
            }

            ModElement procedureModElement = workspace.getModElementByName(procedureName);
            if (procedureModElement == null || procedureModElement.getType() != ModElementType.PROCEDURE
                    || !(procedureModElement.getGeneratableElement() instanceof net.mcreator.element.types.Procedure procedure)) {
                throw new IllegalArgumentException("No procedure mod element named '" + procedureName + "' exists");
            }
            ensureProcedureCompatible(hook, procedure);

            hook.setter().accept(item, new Procedure(procedureName));
            workspace.markDirty();
            workspace.getModElementManager().storeModElement(item);
            workspace.getGenerator().generateBase();
            workspace.getGenerator().generateElement(item);
            workspace.getModElementManager().storeModElementPicture(item);
            itemModElement.reloadElementIcon();
            itemModElement.reinit(workspace);

            // The procedure's dependency metadata did not change, so ProcedureGUI's caller-regeneration
            // path is intentionally not needed here. The changed caller (this item) is generated above.
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("itemName", itemModElement.getName());
            result.put("hook", hookName);
            result.put("procedureName", procedureName);
            result.put("attached", true);
            return result;
    }

    /** Attaches to the public Procedure-typed fields declared directly by Block in MCreator 2026.2. */
    @Override public Object attachBlockProcedure(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Object result = attachBlockProcedure(currentWorkspace(), requiredString(arguments, "blockName"),
                    requiredString(arguments, "hook"), requiredString(arguments, "procedureName"));
            requireMCreator().reloadWorkspaceTabContents();
            return result;
        });
    }

    static Object attachBlockProcedure(Workspace workspace, String blockName, String hookName, String procedureName)
            throws Exception {
        ModElement blockModElement = workspace.getModElementByName(blockName);
        if (blockModElement == null || blockModElement.getType() != ModElementType.BLOCK
                || !(blockModElement.getGeneratableElement() instanceof net.mcreator.element.types.Block block)) {
            throw new IllegalArgumentException("No block mod element named '" + blockName + "' exists");
        }

        BlockProcedureHook hook = blockProcedureHooks().get(hookName);
        if (hook == null) {
            throw new IllegalArgumentException("Unsupported block procedure hook '" + hookName
                    + "'. Supported hooks: " + String.join(", ", blockProcedureHooks().keySet()));
        }

        ModElement procedureModElement = workspace.getModElementByName(procedureName);
        if (procedureModElement == null || procedureModElement.getType() != ModElementType.PROCEDURE
                || !(procedureModElement.getGeneratableElement() instanceof net.mcreator.element.types.Procedure procedure)) {
            throw new IllegalArgumentException("No procedure mod element named '" + procedureName + "' exists");
        }
        ensureProcedureCompatible(hook, procedure);

        hook.setter().accept(block, new Procedure(procedureName));
        workspace.markDirty();
        workspace.getModElementManager().storeModElement(block);
        workspace.getGenerator().generateBase();
        workspace.getGenerator().generateElement(block);
        workspace.getModElementManager().storeModElementPicture(block);
        blockModElement.reloadElementIcon();
        blockModElement.reinit(workspace);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blockName", blockModElement.getName());
        result.put("hook", hookName);
        result.put("procedureName", procedureName);
        result.put("attached", true);
        return result;
    }

    private static void ensureProcedureCompatible(ProcedureHook hook, net.mcreator.element.types.Procedure procedure) {
        List<String> missingDependencies = new ArrayList<>();
        for (Dependency dependency : procedure.getDependencies()) {
            if (!hook.providedDependencies().contains(dependency)) {
                missingDependencies.add(dependency.name() + ":" + dependency.type());
            }
        }
        if (!missingDependencies.isEmpty()) {
            throw new IllegalArgumentException("Procedure requires dependencies not provided by hook '" + hook.name()
                    + "': " + String.join(", ", missingDependencies));
        }

        Object storedReturnType = procedure.getModElement().getMetadata("return_type");
        VariableType procedureReturnType = storedReturnType instanceof String returnTypeName ?
                VariableTypeLoader.INSTANCE.fromName(returnTypeName) : null;
        boolean correctReturnType = hook.expectedReturnType() == null || procedureReturnType == hook.expectedReturnType();
        if (!correctReturnType && !(procedureReturnType == null && hook.returnTypeOptional())) {
            String actual = storedReturnType instanceof String value ? value : "none";
            throw new IllegalArgumentException("Procedure return type '" + actual + "' is incompatible with hook '"
                    + hook.name() + "' (expected " + hook.expectedReturnType().getName() + ")");
        }
    }

    private static Map<String, ItemProcedureHook> itemProcedureHooks() {
        Map<String, ItemProcedureHook> hooks = new LinkedHashMap<>();
        addItemHook(hooks, "onRightClickedInAir", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack", (item, procedure) -> item.onRightClickedInAir = procedure);
        addItemHook(hooks, "onRightClickedOnBlock", VariableTypeLoader.BuiltInTypes.ACTIONRESULTTYPE, true,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack/direction:direction/blockstate:blockstate", (item, procedure) -> item.onRightClickedOnBlock = procedure);
        addItemHook(hooks, "onCrafted", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack", (item, procedure) -> item.onCrafted = procedure);
        addItemHook(hooks, "onEntityHitWith", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/sourceentity:entity/itemstack:itemstack", (item, procedure) -> item.onEntityHitWith = procedure);
        addItemHook(hooks, "onItemInInventoryTick", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack/slot:number", (item, procedure) -> item.onItemInInventoryTick = procedure);
        addItemHook(hooks, "onItemInUseTick", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack/slot:number", (item, procedure) -> item.onItemInUseTick = procedure);
        addItemHook(hooks, "onStoppedUsing", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack/time:number", (item, procedure) -> item.onStoppedUsing = procedure);
        addItemHook(hooks, "onEntitySwing", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack", (item, procedure) -> item.onEntitySwing = procedure);
        addItemHook(hooks, "onDroppedByPlayer", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack", (item, procedure) -> item.onDroppedByPlayer = procedure);
        addItemHook(hooks, "onFinishUsingItem", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack", (item, procedure) -> item.onFinishUsingItem = procedure);
        addItemHook(hooks, "everyTickWhileUsing", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack/time:number", (item, procedure) -> item.everyTickWhileUsing = procedure);
        addItemHook(hooks, "onItemEntityDestroyed", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack/damagesource:damagesource", (item, procedure) -> item.onItemEntityDestroyed = procedure);
        addItemHook(hooks, "onRangedItemUsed", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack", (item, procedure) -> item.onRangedItemUsed = procedure);
        addItemHook(hooks, "rangedUseCondition", VariableTypeLoader.BuiltInTypes.LOGIC, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack", (item, procedure) -> item.rangedUseCondition = procedure);
        return hooks;
    }

    private static void addItemHook(Map<String, ItemProcedureHook> hooks, String name, VariableType expectedReturnType,
                                    boolean returnTypeOptional, String dependencies, BiConsumer<Item, Procedure> setter) {
        hooks.put(name, new ItemProcedureHook(name, Set.of(Dependency.fromString(dependencies)), expectedReturnType,
                returnTypeOptional, setter));
    }

    private static Map<String, BlockProcedureHook> blockProcedureHooks() {
        Map<String, BlockProcedureHook> hooks = new LinkedHashMap<>();
        addBlockHook(hooks, "onBlockAdded", null, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate/oldState:blockstate/moving:logic", (block, procedure) -> block.onBlockAdded = procedure);
        addBlockHook(hooks, "onNeighbourBlockChanges", null, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate", (block, procedure) -> block.onNeighbourBlockChanges = procedure);
        addBlockHook(hooks, "onTickUpdate", null, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate", (block, procedure) -> block.onTickUpdate = procedure);
        addBlockHook(hooks, "onRandomUpdateEvent", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/blockstate:blockstate", (block, procedure) -> block.onRandomUpdateEvent = procedure);
        addBlockHook(hooks, "onDestroyedByPlayer", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/blockstate:blockstate", (block, procedure) -> block.onDestroyedByPlayer = procedure);
        addBlockHook(hooks, "onDestroyedByExplosion", null, false,
                "x:number/y:number/z:number/world:world", (block, procedure) -> block.onDestroyedByExplosion = procedure);
        addBlockHook(hooks, "onStartToDestroy", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/blockstate:blockstate", (block, procedure) -> block.onStartToDestroy = procedure);
        addBlockHook(hooks, "onEntityCollides", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/blockstate:blockstate", (block, procedure) -> block.onEntityCollides = procedure);
        addBlockHook(hooks, "onEntityWalksOn", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/blockstate:blockstate", (block, procedure) -> block.onEntityWalksOn = procedure);
        addBlockHook(hooks, "onEntityFallsOn", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/blockstate:blockstate/distance:number", (block, procedure) -> block.onEntityFallsOn = procedure);
        addBlockHook(hooks, "onBlockPlayedBy", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/itemstack:itemstack/blockstate:blockstate", (block, procedure) -> block.onBlockPlayedBy = procedure);
        addBlockHook(hooks, "onRightClicked", VariableTypeLoader.BuiltInTypes.ACTIONRESULTTYPE, true,
                "x:number/y:number/z:number/world:world/entity:entity/direction:direction/blockstate:blockstate/hitX:number/hitY:number/hitZ:number", (block, procedure) -> block.onRightClicked = procedure);
        addBlockHook(hooks, "onRedstoneOn", null, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate", (block, procedure) -> block.onRedstoneOn = procedure);
        addBlockHook(hooks, "onRedstoneOff", null, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate", (block, procedure) -> block.onRedstoneOff = procedure);
        addBlockHook(hooks, "onHitByProjectile", null, false,
                "x:number/y:number/z:number/world:world/entity:entity/direction:direction/blockstate:blockstate/hitX:number/hitY:number/hitZ:number", (block, procedure) -> block.onHitByProjectile = procedure);
        addBlockHook(hooks, "onBonemealSuccess", null, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate", (block, procedure) -> block.onBonemealSuccess = procedure);
        addBlockHook(hooks, "placingCondition", VariableTypeLoader.BuiltInTypes.LOGIC, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate", (block, procedure) -> block.placingCondition = procedure);
        addBlockHook(hooks, "isBonemealTargetCondition", VariableTypeLoader.BuiltInTypes.LOGIC, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate/clientSide:logic", (block, procedure) -> block.isBonemealTargetCondition = procedure);
        addBlockHook(hooks, "bonemealSuccessCondition", VariableTypeLoader.BuiltInTypes.LOGIC, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate", (block, procedure) -> block.bonemealSuccessCondition = procedure);
        addBlockHook(hooks, "additionalHarvestCondition", VariableTypeLoader.BuiltInTypes.LOGIC, false,
                "x:number/y:number/z:number/entity:entity/world:world/blockstate:blockstate", (block, procedure) -> block.additionalHarvestCondition = procedure);
        addBlockHook(hooks, "inventoryAutomationTakeCondition", VariableTypeLoader.BuiltInTypes.LOGIC, false,
                "index:number/itemstack:itemstack/direction:direction", (block, procedure) -> block.inventoryAutomationTakeCondition = procedure);
        addBlockHook(hooks, "inventoryAutomationPlaceCondition", VariableTypeLoader.BuiltInTypes.LOGIC, false,
                "index:number/itemstack:itemstack/direction:direction", (block, procedure) -> block.inventoryAutomationPlaceCondition = procedure);
        addBlockHook(hooks, "canReceiveVibrationCondition", VariableTypeLoader.BuiltInTypes.LOGIC, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate/entity:entity/vibrationX:number/vibrationY:number/vibrationZ:number", (block, procedure) -> block.canReceiveVibrationCondition = procedure);
        addBlockHook(hooks, "onReceivedVibration", null, false,
                "x:number/y:number/z:number/world:world/blockstate:blockstate/entity:entity/sourceentity:entity/vibrationX:number/vibrationY:number/vibrationZ:number/distance:number", (block, procedure) -> block.onReceivedVibration = procedure);
        return hooks;
    }

    private static void addBlockHook(Map<String, BlockProcedureHook> hooks, String name, VariableType expectedReturnType,
                                     boolean returnTypeOptional, String dependencies,
                                     BiConsumer<net.mcreator.element.types.Block, Procedure> setter) {
        hooks.put(name, new BlockProcedureHook(name, Set.of(Dependency.fromString(dependencies)), expectedReturnType,
                returnTypeOptional, setter));
    }

    private interface ProcedureHook {
        String name();
        Set<Dependency> providedDependencies();
        VariableType expectedReturnType();
        boolean returnTypeOptional();
    }

    private record ItemProcedureHook(String name, Set<Dependency> providedDependencies, VariableType expectedReturnType,
                                     boolean returnTypeOptional, BiConsumer<Item, Procedure> setter) implements ProcedureHook {}

    private record BlockProcedureHook(String name, Set<Dependency> providedDependencies, VariableType expectedReturnType,
                                      boolean returnTypeOptional,
                                      BiConsumer<net.mcreator.element.types.Block, Procedure> setter) implements ProcedureHook {}

    private static ProcedureValidation validateProcedure(Workspace workspace, String xml) {
        Document document = parseBlocklyXml(xml);

        ModElement temporaryModElement = new ModElement(workspace, "McpProcedureValidation", ModElementType.PROCEDURE);
        net.mcreator.element.types.Procedure temporaryProcedure =
                new net.mcreator.element.types.Procedure(temporaryModElement);
        temporaryProcedure.procedurexml = xml;

        BlocklyToProcedure compiler;
        try {
            compiler = temporaryProcedure.getBlocklyToProcedure(new LinkedHashMap<>());
        } catch (Exception error) {
            throw new IllegalStateException("Blockly procedure compiler failed: " + safeXmlError(error), error);
        }

        List<Object> notes = new ArrayList<>();
        boolean hasErrors = false;
        for (BlocklyCompileNote note : compiler.getCompileNotes()) {
            notes.add(Map.of("level", note.type().name(), "message", note.message()));
            if (note.type() == BlocklyCompileNote.Type.ERROR) hasErrors = true;
        }

        // BlocklyToCode deliberately skips an unknown top-level procedural block with a WARNING.
        // For MCP input validation, accepting that XML would silently discard caller-supplied logic,
        // so make this condition a hard validation error before any future write tool can use it.
        for (String unknownType : unknownProcedureBlockTypes(document)) {
            notes.add(Map.of("level", "ERROR", "message", "Unknown Blockly procedure block type: " + unknownType));
            hasErrors = true;
        }

        List<Object> dependencies = new ArrayList<>();
        for (Dependency dependency : compiler.getDependencies()) {
            dependencies.add(Map.of("name", dependency.name(), "type", dependency.type()));
        }
        return new ProcedureValidation(!hasErrors, notes, dependencies,
                compiler.getReturnType() == null ? null : compiler.getReturnType().getName(),
                compiler.getExternalTrigger() == null ? extractRootTrigger(document) : compiler.getExternalTrigger());
    }

    private record ProcedureValidation(boolean valid, List<Object> notes, List<Object> dependencies, String returnType,
                                       String trigger) {
        Map<String, Object> toResponse() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("valid", valid);
            result.put("notes", notes);
            if (valid) addInference(result);
            return result;
        }

        void addInference(Map<String, Object> result) {
            result.put("dependencies", dependencies);
            result.put("return_type", returnType);
            result.put("trigger", trigger);
        }
    }

    /**
     * Only types whose default new-element flow is confirmed in the 2026.2 core are allowed here.
     * Block defaults are used by OrePackMakerTool and WoodPackMakerTool. Item is the existing,
     * previously validated bridge flow. Other registered types deliberately remain unavailable until
     * their mandatory model/texture/configuration inputs have a dedicated, validated schema.
     */
    private static ModElementType<?> supportedCreationType(String suppliedType) {
        String normalizedType = suppliedType.toLowerCase(java.util.Locale.ROOT);
        if (!normalizedType.equals("item") && !normalizedType.equals("block")) {
            throw new IllegalArgumentException("Unsupported elementType '" + suppliedType
                    + "'. This version safely supports: item, block. Other MCreator element types need a dedicated validated input schema.");
        }

        ModElementType<?> type = ModElementTypeLoader.getModElementType(normalizedType);
        if (type == null) { // Defensive only: the loader normally throws for an unregistered name.
            throw new IllegalArgumentException("MCreator did not register elementType '" + normalizedType + "'");
        }
        return type;
    }

    /** Parses only for well-formed XML. Blockly semantic errors remain compiler notes. */
    private static Document parseBlocklyXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid Blockly XML: " + safeXmlError(error));
        }
    }

    /** Returns the trigger field from the required event_trigger root, when present. */
    private static String extractRootTrigger(Document document) {
        NodeList blocks = document.getElementsByTagName("block");
        for (int index = 0; index < blocks.getLength(); index++) {
            if (!(blocks.item(index) instanceof Element block) || !"event_trigger".equals(block.getAttribute("type"))) continue;
            NodeList fields = block.getElementsByTagName("field");
            for (int fieldIndex = 0; fieldIndex < fields.getLength(); fieldIndex++) {
                if (fields.item(fieldIndex) instanceof Element field && "trigger".equals(field.getAttribute("name"))) {
                    return field.getTextContent();
                }
            }
            return null;
        }
        return null;
    }

    private static List<String> unknownProcedureBlockTypes(Document document) {
        Set<String> knownTypes = new HashSet<>(
                BlocklyLoader.INSTANCE.getBlockLoader(net.mcreator.ui.blockly.BlocklyEditorType.PROCEDURE)
                        .getDefinedBlocks().keySet());
        for (IBlockGenerator generator : InternalBlocksLoader.getInternalBlocks(
                net.mcreator.ui.blockly.BlocklyEditorType.PROCEDURE)) {
            for (String type : generator.getSupportedBlocks()) knownTypes.add(type);
        }
        knownTypes.add(net.mcreator.ui.blockly.BlocklyEditorType.PROCEDURE.startBlockName());

        List<String> unknownTypes = new ArrayList<>();
        NodeList blocks = document.getElementsByTagName("block");
        for (int index = 0; index < blocks.getLength(); index++) {
            if (blocks.item(index) instanceof Element block) {
                String type = block.getAttribute("type");
                if (!type.isBlank() && !knownTypes.contains(type) && !unknownTypes.contains(type)) {
                    unknownTypes.add(type);
                }
            }
        }
        return unknownTypes;
    }

    private static String safeXmlError(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    @Override public Object getWorkspaceInfo(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            WorkspaceSettings settings = workspace.getWorkspaceSettings();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("modName", settings.getModName());
            info.put("modId", settings.getModID());
            info.put("version", settings.getVersion());
            info.put("author", settings.getAuthor());
            info.put("description", settings.getDescription());
            info.put("generator", workspace.getGenerator().getGeneratorName());
            info.put("minecraftVersion", workspace.getGenerator().getGeneratorMinecraftVersion());
            info.put("mcreatorVersion", workspace.getMCreatorVersion());
            info.put("workspaceFolder", workspace.getWorkspaceFolder().getAbsolutePath());
            info.put("elementCount", workspace.getModElements().size());
            return info;
        });
    }

    /**
     * Lists the persisted ModElement definitions as read-only MCP resources. The URI resolves to the
     * exact .mod.json file that ModElementManager.storeModElement writes in the active workspace.
     */
    @Override public Object listResources(Map<String, Object> arguments) throws Exception {
        return onEdt(() -> {
            Workspace workspace = currentWorkspace();
            List<Object> resources = new ArrayList<>();
            for (ModElement element : workspace.getModElements()) {
                Map<String, Object> resource = new LinkedHashMap<>();
                resource.put("uri", elementResourceUri(element.getName()));
                resource.put("name", element.getName());
                resource.put("mimeType", "application/json");
                resource.put("description", "MCreator ModElement (" + element.getType().getRegistryName() + ")");
                resources.add(resource);
            }
            return Map.of("resources", resources);
        });
    }

    /**
     * Resolves a resource while on the EDT, then reads only its already-resolved file off the EDT so
     * disk I/O cannot block the Swing event queue. This operation never changes the workspace.
     */
    @Override public Object readResource(Map<String, Object> arguments) throws Exception {
        String uri = requiredString(arguments, "uri");
        ResourceFile resource = onEdt(() -> resolveElementResource(uri));
        String text = Files.readString(resource.file(), StandardCharsets.UTF_8);
        return Map.of("contents", List.of(Map.of(
                "uri", resource.uri(),
                "mimeType", "application/json",
                "text", text)));
    }

    private ResourceFile resolveElementResource(String uri) {
        final String prefix = "mcreator://element/";
        if (!uri.startsWith(prefix)) {
            throw new IllegalArgumentException("Unsupported resource URI scheme: " + uri);
        }

        String elementName = uri.substring(prefix.length());
        if (elementName.isBlank() || elementName.contains("/") || elementName.contains("\\")) {
            throw new IllegalArgumentException("Invalid MCreator element resource URI: " + uri);
        }

        Workspace workspace = currentWorkspace();
        ModElement element = workspace.getModElementByName(elementName);
        if (element == null) {
            throw new IllegalArgumentException("No mod element named '" + elementName + "' exists in the current workspace");
        }

        // Confirmed in ModElementManager for MCreator 2026.2.33518: <name>.mod.json.
        Path elementFile = workspace.getFolderManager().getModElementsDir().toPath()
                .resolve(element.getName() + ".mod.json").normalize();
        if (!Files.isRegularFile(elementFile)) {
            throw new IllegalStateException("Persisted definition file was not found for mod element '" + element.getName() + "'");
        }
        return new ResourceFile(elementResourceUri(element.getName()), elementFile);
    }

    private static String elementResourceUri(String elementName) {
        return "mcreator://element/" + elementName;
    }

    private record ResourceFile(String uri, Path file) { }

    private Workspace currentWorkspace() {
        return requireMCreator().getWorkspace();
    }

    private MCreator requireMCreator() {
        MCreator current = mcreator;
        if (current == null) throw new IllegalStateException("No MCreator workspace window is open");
        return current;
    }

    private static String requiredString(Map<String, Object> arguments, String key) {
        String value = optionalString(arguments.get(key));
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static String optionalString(Object value) {
        if (!(value instanceof String text)) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> T onEdt(Callable<T> task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return task.call();
        CompletableFuture<T> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try { result.complete(task.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        try { return result.get(); }
        catch (ExecutionException error) {
            if (error.getCause() instanceof Exception exception) throw exception;
            throw new RuntimeException(error.getCause());
        }
    }
}
