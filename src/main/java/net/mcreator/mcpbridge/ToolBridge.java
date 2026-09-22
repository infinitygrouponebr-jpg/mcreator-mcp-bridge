package net.mcreator.mcpbridge;

import java.util.Map;

interface ToolBridge {
    Object listModElements(Map<String, Object> arguments) throws Exception;
    Object createItem(Map<String, Object> arguments) throws Exception;
    Object createModElement(Map<String, Object> arguments) throws Exception;
    Object validateProcedureXml(Map<String, Object> arguments) throws Exception;
    Object createProcedure(Map<String, Object> arguments) throws Exception;
    Object attachItemProcedure(Map<String, Object> arguments) throws Exception;
    Object attachBlockProcedure(Map<String, Object> arguments) throws Exception;
    Object getWorkspaceInfo(Map<String, Object> arguments) throws Exception;
    Object listElementTypes(Map<String, Object> arguments) throws Exception;
    Object getElementSchema(Map<String, Object> arguments) throws Exception;
    Object checkGeckolibBlockAssets(Map<String, Object> arguments) throws Exception;
    Object createCuriosSlot(Map<String, Object> arguments) throws Exception;
    Object createCuriosBauble(Map<String, Object> arguments) throws Exception;
    Object createPatchouliBook(Map<String, Object> arguments) throws Exception;
    Object createPatchouliCategory(Map<String, Object> arguments) throws Exception;
    Object createPatchouliEntry(Map<String, Object> arguments) throws Exception;
    Object listResources(Map<String, Object> arguments) throws Exception;
    Object readResource(Map<String, Object> arguments) throws Exception;
    Object listWorkspaceVariables(Map<String, Object> arguments) throws Exception;
    Object createWorkspaceVariable(Map<String, Object> arguments) throws Exception;
    Object updateWorkspaceVariable(Map<String, Object> arguments) throws Exception;
    Object deleteWorkspaceVariable(Map<String, Object> arguments) throws Exception;
    Object createCodeElement(Map<String, Object> arguments) throws Exception;
    Object readCodeElement(Map<String, Object> arguments) throws Exception;
    Object writeCodeElement(Map<String, Object> arguments) throws Exception;
    Object appendProcedureJavaSnippet(Map<String, Object> arguments) throws Exception;
    Object createElementFromJson(Map<String, Object> arguments) throws Exception;
    Object updateElementFromJson(Map<String, Object> arguments) throws Exception;
    Object getElementDefinition(Map<String, Object> arguments) throws Exception;
    Object listWorkspaceAssets(Map<String, Object> arguments) throws Exception;
    Object readWorkspaceAsset(Map<String, Object> arguments) throws Exception;
    Object updateProcedure(Map<String, Object> arguments) throws Exception;
    Object deleteModElement(Map<String, Object> arguments) throws Exception;
    Object runBuild(Map<String, Object> arguments) throws Exception;
    Object getLastBuildLog(Map<String, Object> arguments) throws Exception;
    Object captureMCreatorWindow(Map<String, Object> arguments) throws Exception;
    void authorizeTool(String toolName) throws Exception;
}
