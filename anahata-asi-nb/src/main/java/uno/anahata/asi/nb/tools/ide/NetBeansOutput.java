/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.ide;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import uno.anahata.asi.swing.internal.SwingUtils;

/**
 * Static utility for interacting with the NetBeans Output Window and its tabs.
 * <p>
 * This class provides methods to discover, inspect, and extract content from 
 * the various tabs in the IDE's Output Window (e.g., build logs, version control output).
 * It leverages deep component hierarchy inspection to find the underlying 
 * {@link JTextComponent} instances for each tab.
 * </p>
 * 
 * @author anahata
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NetBeansOutput {

    /** The internal NetBeans class name for an output tab component. */
    private static final String OUTPUT_TAB_CLASS = "org.netbeans.core.output2.OutputTab";

    /**
     * Gathers information about all open output tabs and returns a Markdown report with tails.
     * This method performs a single EDT run to gather all data.
     * 
     * @return A Markdown string.
     * @throws Exception if gathering fails.
     */
    public static String getMarkdownReport() throws Exception {
        long start = System.currentTimeMillis();
        final StringBuilder sb = new StringBuilder("### Open Output Tabs\n\n");
        
        SwingUtils.runInEDTAndWait(() -> {
            TopComponent outputTC = WindowManager.getDefault().findTopComponent("output");
            if (outputTC == null) {
                sb.append("Output window is not open.");
                return;
            }

            List<OutputTabInfo> tabs = new ArrayList<>();
            findOutputTabsRecursive(outputTC, tabs);

            if (tabs.isEmpty()) {
                sb.append("No output tabs are currently open.");
            } else {
                for (OutputTabInfo tab : tabs) {
                    String cleanName = tab.getDisplayName().replaceAll("<[^>]*>", "");
                    sb.append(String.format("#### %s\n", cleanName));
                    sb.append(String.format("- **ID**: %d\n", tab.getId()));
                    sb.append(String.format("- **Lines**: %d\n", tab.getTotalLines()));
                    sb.append(String.format("- **Running**: %s\n\n", tab.isRunning()));
                    sb.append("- **Last 20 lines**:\n");
                    
                    String tail = getTabTailStatic(tab.getId(), 20);
                    if (!tail.isBlank()) {
                        sb.append("```text\n").append(tail).append("\n```\n\n");
                    }
                }
            }
        });
        log.info("Output Tabs Markdown report generated in {}ms", System.currentTimeMillis() - start);
        return sb.toString();
    }

    /**
     * Lists all open output tabs.
     * 
     * @return A list of OutputTabInfo.
     * @throws Exception if gathering fails.
     */
    public static List<OutputTabInfo> listOutputTabs() throws Exception {
        long start = System.currentTimeMillis();
        final List<OutputTabInfo> tabInfos = new ArrayList<>();
        SwingUtils.runInEDTAndWait(() -> {
            TopComponent outputTC = WindowManager.getDefault().findTopComponent("output");
            if (outputTC != null) {
                findOutputTabsRecursive(outputTC, tabInfos);
            }
        });
        log.info("Gathered info for {} output tabs in {}ms", tabInfos.size(), System.currentTimeMillis() - start);
        return tabInfos;
    }

    /**
     * Retrieves the full text content of a specific output tab.
     * 
     * @param id The tab identifier.
     * @return The text content.
     * @throws Exception if the tab is not found or content cannot be read.
     */
    public static String getTabContent(long id) throws Exception {
        final StringBuilder sb = new StringBuilder();
        SwingUtils.runInEDTAndWait(() -> {
            findTextComponentById(id).ifPresent(textComp -> sb.append(textComp.getText()));
        });
        
        if (sb.length() == 0) {
            throw new Exception("Output tab not found or empty: " + id);
        }
        return sb.toString();
    }

    /**
     * Retrieves the last few lines of text from a specific output tab efficiently.
     * <p>
     * Instead of retrieving the entire document text, this method only extracts the 
     * end of the document to minimize memory pressure.
     * </p>
     * 
     * @param id The tab identifier.
     * @param maxLines The maximum number of lines to retrieve.
     * @return The tail of the output content, or an empty string if not found or empty.
     */
    public static String getTabTailStatic(long id, int maxLines) {
        final StringBuilder sb = new StringBuilder();
        findTextComponentById(id).ifPresent(textComp -> {
            try {
                Document doc = textComp.getDocument();
                int length = doc.getLength();
                int maxChars = 2048; // Sufficient for 20-50 lines
                int offset = Math.max(0, length - maxChars);
                String text = doc.getText(offset, length - offset);
                
                if (text != null && !text.isBlank()) {
                    List<String> lines = text.lines().collect(Collectors.toList());
                    int count = lines.size();
                    int skip = Math.max(0, count - maxLines);
                    sb.append(lines.stream().skip(skip).collect(Collectors.joining("\n")));
                }
            } catch (Exception e) {
                log.error("Failed to extract tail for output tab " + id, e);
            }
        });
        return sb.toString();
    }

    /**
     * Recursively traverses the component hierarchy to find all output tabs.
     * 
     * @param component The starting component.
     * @param tabInfos The list to populate with found tab information.
     */
    private static void findOutputTabsRecursive(Component component, List<OutputTabInfo> tabInfos) {
        if (component == null) return;

        if (component.getClass().getName().equals(OUTPUT_TAB_CLASS)) {
            String title = component.getName();
            boolean isRunning = title != null && title.contains("<b>");

            findTextComponent(component).ifPresent(textComponent -> {
                String text = textComponent.getText();
                int contentSize = text != null ? text.length() : 0;
                int totalLines = text != null ? (int) text.lines().count() : 0;
                long id = System.identityHashCode(textComponent);
                tabInfos.add(new OutputTabInfo(id, title, contentSize, totalLines, isRunning));
            });
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                findOutputTabsRecursive(child, tabInfos);
            }
        }
    }

    /**
     * Finds the first JTextComponent within a component hierarchy.
     * 
     * @param comp The root component.
     * @return An Optional containing the JTextComponent if found.
     */
    private static Optional<JTextComponent> findTextComponent(Component comp) {
        if (comp instanceof JEditorPane) return Optional.of((JEditorPane) comp);
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                Optional<JTextComponent> found = findTextComponent(child);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a text component by its identity hash code.
     * 
     * @param id The identity hash code.
     * @return An Optional containing the JTextComponent if found.
     */
    private static Optional<JTextComponent> findTextComponentById(long id) {
        TopComponent outputTC = WindowManager.getDefault().findTopComponent("output");
        return outputTC != null ? findTextComponentRecursive(outputTC, id) : Optional.empty();
    }
    
    /**
     * Recursively searches for a text component with a matching identity hash code.
     * 
     * @param component The starting component.
     * @param targetId The target identity hash code.
     * @return An Optional containing the JTextComponent if found.
     */
    private static Optional<JTextComponent> findTextComponentRecursive(Component component, long targetId) {
        if (component == null) return Optional.empty();
        if (component instanceof JTextComponent && System.identityHashCode(component) == targetId) {
            return Optional.of((JTextComponent) component);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Optional<JTextComponent> found = findTextComponentRecursive(child, targetId);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }
}
