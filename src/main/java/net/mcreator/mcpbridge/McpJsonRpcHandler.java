package net.mcreator.mcpbridge;

import java.util.*;

/** Transport-agnostic JSON-RPC 2.0 dispatcher for the MCP server surface. */
final class McpJsonRpcHandler {
    private final ToolBridge tools;

    McpJsonRpcHandler(ToolBridge tools) { this.tools = Objects.requireNonNull(tools); }

    /** @return a JSON-RPC response, or null when the input is a notification. */
    @SuppressWarnings("unchecked")
    String handle(String message) {
        Object id = null;
        boolean requestHadId = false;
        try {
            Object decoded = Json.parse(message);
            if (!(decoded instanceof Map<?, ?> raw)) return error(null, -32600, "Request must be an object");
            Map<String, Object> request = (Map<String, Object>) raw;
            requestHadId = request.containsKey("id");
            id = request.get("id");
            if (!"2.0".equals(request.get("jsonrpc"))) throw new IllegalArgumentException("jsonrpc must be 2.0");
            String method = string(request.get("method"));
            if (method == null) throw new IllegalArgumentException("Missing method");
            Object result = dispatch(method, object(request.get("params")));
            return requestHadId ? response(id, result) : null;
        } catch (ToolNotImplementedException error) {
            return requestHadId ? response(id, toolText(error.getMessage(), true)) : null;
        } catch (Exception error) {
            return requestHadId ? error(id, -32600, error.getMessage()) : error(null, -32700, "Parse error: " + error.getMessage());
        }
    }

    private Object dispatch(String method, Map<String, Object> params) throws Exception {
        return switch (method) {
            case "initialize" -> Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(
                    "tools", Map.of("listChanged", false),
                    "resources", Map.of("subscribe", false, "listChanged", false)),
                    "serverInfo", Map.of("name", "mcreator-mcp-bridge", "version", "0.1.0"));
            case "ping" -> Map.of();
            case "notifications/initialized", "notifications/cancelled" -> null;
            case "tools/list" -> Map.of("tools", toolDefinitions());
            case "tools/call" -> callTool(params);
            case "resources/list" -> tools.listResources(params);
            case "resources/read" -> tools.readResource(params);
            default -> throw new IllegalArgumentException("Method not found: " + method);
        };
    }

    private List<Object> toolDefinitions() {
        return List.of(
                Map.of("name", "list_mod_elements", "description", "List mod elements in the current MCreator workspace.", "inputSchema", Map.of("type", "object", "properties", Map.of("elementType", Map.of("type", "string", "description", "Optional MCreator mod element type registry name, for example item.")), "additionalProperties", false)),
                Map.of("name", "list_workspace_variables", "description", "List global workspace variables.", "inputSchema", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),
                Map.of("name", "create_workspace_variable", "description", "Create a global MCreator workspace variable.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "type", Map.of("type", "string"), "scope", Map.of("type", "string"), "value", Map.of()), "required", List.of("name", "type"), "additionalProperties", false)),
                Map.of("name", "update_workspace_variable", "description", "Update type, scope, or initial value of a global workspace variable.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "type", Map.of("type", "string"), "scope", Map.of("type", "string"), "value", Map.of()), "required", List.of("name"), "additionalProperties", false)),
                Map.of("name", "delete_workspace_variable", "description", "Delete a global workspace variable.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")), "required", List.of("name"), "additionalProperties", false)),
                Map.of("name", "create_code_element", "description", "Create an MCreator code-locked Java element and replace its generated Java file with complete source code.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string", "description", "New Java class / MCreator code element name."), "source", Map.of("type", "string", "description", "Complete Java source for the generated code element file.")), "required", List.of("name", "source"), "additionalProperties", false)),
                Map.of("name", "read_code_element", "description", "Read the Java source file of an existing MCreator code element.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")), "required", List.of("name"), "additionalProperties", false)),
                Map.of("name", "write_code_element", "description", "Replace the Java source file of an existing MCreator code element. It cannot modify generated item, block, or procedure code.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "source", Map.of("type", "string", "description", "Complete replacement Java source.")), "required", List.of("name", "source"), "additionalProperties", false)),
                Map.of("name", "run_build", "description", "Start a Gradle build, client run, or export through MCreator's configured Gradle Tooling connection. Poll get_last_build_log for captured output.", "inputSchema", Map.of("type", "object", "properties", Map.of("action", Map.of("type", "string", "enum", List.of("build", "client", "export"))), "additionalProperties", false)),
                Map.of("name", "get_last_build_log", "description", "Return the status and captured stdout/stderr of the most recent MCP Gradle task.", "inputSchema", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),
                Map.of("name", "capture_mcreator_window", "description", "Capture the currently rendered MCreator window as a scaled PNG data URI. This captures the visible UI, not a hidden editor model.", "inputSchema", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),
                Map.of("name", "create_item", "description", "Create an item in the current MCreator workspace.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string", "description", "Item registry/display name.")), "required", List.of("name"), "additionalProperties", false)),
                Map.of("name", "create_mod_element", "description", "Create a mod element with MCreator defaults. Currently supports item and block only.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string", "description", "Mod element display/name source."), "elementType", Map.of("type", "string", "enum", List.of("item", "block"), "description", "MCreator element type.")), "required", List.of("name", "elementType"), "additionalProperties", false)),
                Map.of("name", "validate_procedure_xml", "description", "Validate Blockly XML for an MCreator procedure without modifying the workspace.", "inputSchema", Map.of("type", "object", "properties", Map.of("xml", Map.of("type", "string", "description", "Complete Blockly procedure XML, rooted at event_trigger.")), "required", List.of("xml"), "additionalProperties", false)),
                Map.of("name", "create_procedure", "description", "Validate and create a Blockly procedure in the current MCreator workspace.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string", "description", "New procedure mod element name."), "xml", Map.of("type", "string", "description", "Complete Blockly procedure XML, rooted at event_trigger.")), "required", List.of("name", "xml"), "additionalProperties", false)),
                Map.of("name", "update_procedure", "description", "Validate complete Blockly XML, then atomically replace and regenerate an existing procedure. Requires Swing confirmation by default.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "xml", Map.of("type", "string")), "required", List.of("name", "xml"), "additionalProperties", false)),
                Map.of("name", "append_procedure_java_snippet", "description", "Append MCreator's built-in java_code Blockly block to an existing procedure. The snippet is emitted verbatim as Java and is not syntax-checked until the workspace is built.", "inputSchema", Map.of("type", "object", "properties", Map.of("procedureName", Map.of("type", "string"), "code", Map.of("type", "string", "description", "Java statements valid at the procedure insertion point.")), "required", List.of("procedureName", "code"), "additionalProperties", false)),
                Map.of("name", "attach_item_procedure", "description", "Attach a compatible existing procedure to a validated item event hook.", "inputSchema", Map.of("type", "object", "properties", Map.of("itemName", Map.of("type", "string", "description", "Existing item mod element name."), "hook", Map.of("type", "string", "description", "Supported public Item procedure hook name."), "procedureName", Map.of("type", "string", "description", "Existing procedure mod element name.")), "required", List.of("itemName", "hook", "procedureName"), "additionalProperties", false)),
                Map.of("name", "attach_block_procedure", "description", "Attach a compatible existing procedure to a validated block event or condition hook.", "inputSchema", Map.of("type", "object", "properties", Map.of("blockName", Map.of("type", "string", "description", "Existing block mod element name."), "hook", Map.of("type", "string", "description", "Supported public Block Procedure hook name."), "procedureName", Map.of("type", "string", "description", "Existing procedure mod element name.")), "required", List.of("blockName", "hook", "procedureName"), "additionalProperties", false)),
                Map.of("name", "list_element_types", "description", "List every ModElement type identifier registered by the current MCreator runtime.", "inputSchema", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),
                Map.of("name", "get_element_schema", "description", "Create an in-memory default .mod.json template for a registered MCreator element type without writing it to the workspace.", "inputSchema", Map.of("type", "object", "properties", Map.of("element_type", Map.of("type", "string", "description", "Registered MCreator ModElement type, for example item, block, or livingentity.")), "required", List.of("element_type"), "additionalProperties", false)),
                Map.of("name", "create_element_from_json", "description", "Create any registered JSON-persisted MCreator element by merging definition fields over MCreator's current runtime defaults, then validating and generating it.", "inputSchema", Map.of("type", "object", "properties", Map.of("elementType", Map.of("type", "string"), "name", Map.of("type", "string"), "definition", Map.of("type", "object", "description", "Any serializable fields for this element type. Obtain a live template with get_element_schema first.")), "required", List.of("elementType", "name", "definition"), "additionalProperties", false)),
                Map.of("name", "update_element_from_json", "description", "Merge supplied serializable fields into an existing JSON-persisted ModElement, validate with MCreator, persist, and regenerate it.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "definition", Map.of("type", "object")), "required", List.of("name", "definition"), "additionalProperties", false)),
                Map.of("name", "delete_mod_element", "description", "Delete one existing ModElement through MCreator's Workspace deletion lifecycle. Requires Swing confirmation by default.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")), "required", List.of("name"), "additionalProperties", false)),
                Map.of("name", "get_element_definition", "description", "Return the exact current MCreator JSON definition envelope of an existing ModElement.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")), "required", List.of("name"), "additionalProperties", false)),
                Map.of("name", "list_workspace_assets", "description", "List user-imported files under the current workspace src/main/resources tree without changing them.", "inputSchema", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),
                Map.of("name", "read_workspace_asset", "description", "Read a text workspace asset (.json, .mcmeta, .lang, or .txt) by its path relative to src/main/resources.", "inputSchema", Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string")), "required", List.of("path"), "additionalProperties", false)),
                Map.of("name", "check_geckolib_block_assets", "description", "Read-only diagnostic for GeckoLib Reborn animated-block model, texture, and animation assets. Does not create or modify any workspace file.", "inputSchema", Map.of("type", "object", "properties", Map.of("modelName", Map.of("type", "string", "description", "Asset base name without .geo.json or .animation.json, for example storage.")), "required", List.of("modelName"), "additionalProperties", false)),
                Map.of("name", "create_curios_slot", "description", "Best-effort: create a Nerdy's Curios API custom slot through its runtime storage class. Requires the curios_api workspace dependency and an imported SCREEN texture.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "textureName", Map.of("type", "string", "description", "Imported SCREEN texture base name, without .png."), "slotName", Map.of("type", "string"), "amount", Map.of("type", "integer", "minimum", 1), "modelToggling", Map.of("type", "boolean"), "changeOrder", Map.of("type", "boolean"), "slotOrder", Map.of("type", "integer", "minimum", 0)), "required", List.of("name", "textureName"), "additionalProperties", false)),
                Map.of("name", "create_curios_bauble", "description", "Best-effort: associate an existing item with a Curios slot. Custom Java model and procedure hooks remain UI-only.", "inputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "itemName", Map.of("type", "string", "description", "Existing item ModElement name."), "slotType", Map.of("type", "string", "description", "Built-in Curios slot, or exact existing curiosslot name."), "slotAmount", Map.of("type", "integer", "minimum", 1), "addSlot", Map.of("type", "boolean"), "enderMask", Map.of("type", "boolean"), "friendlyPigs", Map.of("type", "boolean"), "snowWalk", Map.of("type", "boolean")), "required", List.of("name", "itemName", "slotType"), "additionalProperties", false)),
                Map.of("name", "create_patchouli_book", "description", "Create the data and resource-pack folder trees plus a minimal valid Patchouli 1.21.x book.json for the current mod.", "inputSchema", Map.of("type", "object", "properties", Map.of("bookId", Map.of("type", "string", "description", "Lowercase Patchouli internal book ID, using letters and underscores."), "name", Map.of("type", "string", "description", "Displayed title of the Patchouli book.")), "required", List.of("bookId", "name"), "additionalProperties", false)),
                Map.of("name", "create_patchouli_category", "description", "Create a Patchouli category JSON file in an existing book. Its required icon defaults to minecraft:book.", "inputSchema", Map.of("type", "object", "properties", Map.of("bookId", Map.of("type", "string"), "categoryId", Map.of("type", "string"), "name", Map.of("type", "string"), "description", Map.of("type", "string")), "required", List.of("bookId", "categoryId", "name", "description"), "additionalProperties", false)),
                Map.of("name", "create_patchouli_entry", "description", "Create a Patchouli entry with patchouli:text pages in an existing category.", "inputSchema", Map.of("type", "object", "properties", Map.of("bookId", Map.of("type", "string"), "categoryId", Map.of("type", "string"), "entryId", Map.of("type", "string"), "name", Map.of("type", "string"), "icon", Map.of("type", "string", "description", "Namespaced registry ID of an item from the current workspace."), "texts", Map.of("type", "array", "items", Map.of("type", "string"))), "required", List.of("bookId", "categoryId", "entryId", "name", "icon", "texts"), "additionalProperties", false)),
                Map.of("name", "get_workspace_info", "description", "Get metadata for the currently open MCreator workspace.", "inputSchema", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)));
    }

    private Object callTool(Map<String, Object> params) throws Exception {
        String name = string(params.get("name"));
        if (name == null) throw new IllegalArgumentException("tools/call requires name");
        tools.authorizeTool(name);
        Object value = switch (name) {
            case "list_mod_elements" -> tools.listModElements(object(params.get("arguments")));
            case "list_workspace_variables" -> tools.listWorkspaceVariables(object(params.get("arguments")));
            case "create_workspace_variable" -> tools.createWorkspaceVariable(object(params.get("arguments")));
            case "update_workspace_variable" -> tools.updateWorkspaceVariable(object(params.get("arguments")));
            case "delete_workspace_variable" -> tools.deleteWorkspaceVariable(object(params.get("arguments")));
            case "create_code_element" -> tools.createCodeElement(object(params.get("arguments")));
            case "read_code_element" -> tools.readCodeElement(object(params.get("arguments")));
            case "write_code_element" -> tools.writeCodeElement(object(params.get("arguments")));
            case "run_build" -> tools.runBuild(object(params.get("arguments")));
            case "get_last_build_log" -> tools.getLastBuildLog(object(params.get("arguments")));
            case "capture_mcreator_window" -> tools.captureMCreatorWindow(object(params.get("arguments")));
            case "create_item" -> tools.createItem(object(params.get("arguments")));
            case "create_mod_element" -> tools.createModElement(object(params.get("arguments")));
            case "validate_procedure_xml" -> tools.validateProcedureXml(object(params.get("arguments")));
            case "create_procedure" -> tools.createProcedure(object(params.get("arguments")));
            case "update_procedure" -> tools.updateProcedure(object(params.get("arguments")));
            case "append_procedure_java_snippet" -> tools.appendProcedureJavaSnippet(object(params.get("arguments")));
            case "attach_item_procedure" -> tools.attachItemProcedure(object(params.get("arguments")));
            case "attach_block_procedure" -> tools.attachBlockProcedure(object(params.get("arguments")));
            case "list_element_types" -> tools.listElementTypes(object(params.get("arguments")));
            case "get_element_schema" -> tools.getElementSchema(object(params.get("arguments")));
            case "create_element_from_json" -> tools.createElementFromJson(object(params.get("arguments")));
            case "update_element_from_json" -> tools.updateElementFromJson(object(params.get("arguments")));
            case "delete_mod_element" -> tools.deleteModElement(object(params.get("arguments")));
            case "get_element_definition" -> tools.getElementDefinition(object(params.get("arguments")));
            case "list_workspace_assets" -> tools.listWorkspaceAssets(object(params.get("arguments")));
            case "read_workspace_asset" -> tools.readWorkspaceAsset(object(params.get("arguments")));
            case "check_geckolib_block_assets" -> tools.checkGeckolibBlockAssets(object(params.get("arguments")));
            case "create_curios_slot" -> tools.createCuriosSlot(object(params.get("arguments")));
            case "create_curios_bauble" -> tools.createCuriosBauble(object(params.get("arguments")));
            case "create_patchouli_book" -> tools.createPatchouliBook(object(params.get("arguments")));
            case "create_patchouli_category" -> tools.createPatchouliCategory(object(params.get("arguments")));
            case "create_patchouli_entry" -> tools.createPatchouliEntry(object(params.get("arguments")));
            case "get_workspace_info" -> tools.getWorkspaceInfo(object(params.get("arguments")));
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
        return toolText(Json.stringify(value), false);
    }

    private static String response(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0"); response.put("id", id); response.put("result", result);
        return Json.stringify(response);
    }
    private static String error(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0"); response.put("id", id);
        response.put("error", Map.of("code", code, "message", message == null ? "Invalid request" : message));
        return Json.stringify(response);
    }
    private static Map<String, Object> toolText(String text, boolean isError) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(Map.of("type", "text", "text", text)));
        if (isError) result.put("isError", true);
        return result;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>(); }
    private static String string(Object value) { return value instanceof String text ? text : null; }
}
