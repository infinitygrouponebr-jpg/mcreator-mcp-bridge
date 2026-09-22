package net.mcreator.mcpbridge;

import net.mcreator.ui.MCreator;

import javax.swing.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Central policy gate for MCP mutations.  The dialog always runs on the Swing EDT. */
final class McpAccessController {
    private static final Set<String> MUTATING = Set.of(
            "create_workspace_variable", "update_workspace_variable", "delete_workspace_variable",
            "create_code_element", "write_code_element", "create_item", "create_mod_element",
            "create_procedure", "update_procedure", "append_procedure_java_snippet",
            "attach_item_procedure", "attach_block_procedure", "create_element_from_json",
            "update_element_from_json", "delete_mod_element", "run_build", "run_client", "export_mod",
            "create_curios_slot", "create_curios_bauble", "create_patchouli_book",
            "create_patchouli_category", "create_patchouli_entry");
    private static final Set<String> CONFIRM = Set.of("write_code_element", "update_element_from_json",
            "update_procedure", "delete_mod_element");

    private final AtomicBoolean readOnly = new AtomicBoolean(Boolean.getBoolean("mcreator.mcp.read_only"));
    private final AtomicBoolean confirmations = new AtomicBoolean(!Boolean.getBoolean("mcreator.mcp.confirmations.disabled"));

    boolean isReadOnly() { return readOnly.get(); }
    void setReadOnly(boolean value) { readOnly.set(value); }
    boolean confirmationsEnabled() { return confirmations.get(); }
    void setConfirmationsEnabled(boolean value) { confirmations.set(value); }

    void authorize(MCreator mcreator, String toolName) {
        if (!MUTATING.contains(toolName)) return;
        if (readOnly.get()) throw new IllegalStateException("MCP Bridge is in read-only mode; '" + toolName + "' is blocked");
        if (!CONFIRM.contains(toolName) || !confirmations.get()) return;
        final int[] response = new int[1];
        Runnable prompt = () -> response[0] = JOptionPane.showConfirmDialog(mcreator,
                "An MCP client requested the critical action:\n\n" + toolName
                        + "\n\nApprove this action for the current workspace?",
                "MCreator MCP Bridge confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (SwingUtilities.isEventDispatchThread()) prompt.run();
        else {
            try { SwingUtilities.invokeAndWait(prompt); }
            catch (Exception error) { throw new IllegalStateException("Could not show MCP confirmation dialog", error); }
        }
        if (response[0] != JOptionPane.YES_OPTION) throw new IllegalStateException("Action rejected in MCreator confirmation dialog");
    }
}
