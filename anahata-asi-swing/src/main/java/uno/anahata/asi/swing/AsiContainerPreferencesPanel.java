/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;
import uno.anahata.asi.AsiContainerPreferences;
import uno.anahata.asi.agi.AgiConfig;
import uno.anahata.asi.agi.provider.AbstractAgiProvider;
import uno.anahata.asi.agi.provider.AbstractModel;
import uno.anahata.asi.swing.agi.config.SessionConfigPanel;
import uno.anahata.asi.swing.icons.OkIcon;

/**
 * A centralized, multi-tabbed Command Center for managing the ASI container.
 * It governs global defaults, DNA templates, and per-provider API key pools.
 * 
 * @author anahata
 */
@Slf4j
public class AsiContainerPreferencesPanel extends JPanel {

    /** The parent ASI container instance. */
    private final AbstractSwingAsiContainer container;
    /** The global ASI preferences being edited. */
    private final AsiContainerPreferences prefs;
    
    /** Dropdown for selecting the default AI provider for the container. */
    private JComboBox<String> providerDropdown;
    /** Dropdown for selecting the default AI model for the container. */
    private JComboBox<String> modelDropdown;

    private final JTabbedPane mainTabs;

    /**
     * Constructs a new preferences Command Center, defaulting to the first tab.
     * 
     * @param container The ASI container instance.
     */
    public AsiContainerPreferencesPanel(AbstractSwingAsiContainer container) {
        this(container, 0);
    }

    /**
     * Constructs a new preferences Command Center with a specific tab selected.
     * 
     * @param container The ASI container instance.
     * @param initialTabIndex The index of the tab to select initially.
     */
    public AsiContainerPreferencesPanel(AbstractSwingAsiContainer container, int initialTabIndex) {
        this.container = container;
        this.prefs = container.getPreferences();
        
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(950, 750));

        this.mainTabs = new JTabbedPane();
        mainTabs.addTab("General Defaults", createGeneralTab());
        mainTabs.addTab("DNA Templates", createTemplatesTab());
        mainTabs.addTab("Tool Permissions", new ToolkitPermissionsPanel(container));
        mainTabs.addTab("API Key Pools", createApiKeysTab());

        if (initialTabIndex >= 0 && initialTabIndex < mainTabs.getTabCount()) {
            mainTabs.setSelectedIndex(initialTabIndex);
        }

        add(mainTabs, BorderLayout.CENTER);
        
        // Footer: Save Button
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save & Apply Global Config", new OkIcon(16));
        saveBtn.addActionListener(e -> {
            container.savePreferences();
            log.info("Global preferences persisted to disk.");
        });
        footer.add(saveBtn);
        add(footer, BorderLayout.SOUTH);
        
        // Initialize model discovery
        refreshModelDropdown();
    }

    private JPanel createGeneralTab() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 20", "[right]15[grow,fill]"));
        
        JLabel title = new JLabel("Global 'Starting XI' Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, "span, wrap, gapbottom 20");

        // 1. Default Provider
        panel.add(new JLabel("Default AI Provider:"));
        providerDropdown = new JComboBox<>();
        AgiConfig template = prefs.getAgiTemplate();
        DefaultComboBoxModel<String> providerModel = new DefaultComboBoxModel<>();
        template.getProviderUuids().forEach(providerModel::addElement);
        providerDropdown.setModel(providerModel);
        
        providerDropdown.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String uuid) {
                    AbstractAgiProvider p = container.getProvider(uuid);
                    if (p != null) {
                        setText(p.getDisplayName() + " (" + p.getUuid() + ")");
                    } else {
                        setText(uuid);
                    }
                }
                return this;
            }
        });
        
        providerDropdown.setSelectedItem(template.getSelectedProviderUuid());
        providerDropdown.addActionListener(e -> {
            String selected = (String) providerDropdown.getSelectedItem();
            template.setSelectedProviderUuid(selected);
            refreshModelDropdown();
        });
        panel.add(providerDropdown, "wrap");

        // 2. Default Model
        panel.add(new JLabel("Default AI Model:"));
        modelDropdown = new JComboBox<>();
        modelDropdown.addActionListener(e -> {
            String selected = (String) modelDropdown.getSelectedItem();
            if (selected != null) {
                template.setSelectedModelId(selected);
            }
        });
        panel.add(modelDropdown, "wrap");
        
        panel.add(new JLabel("<html><font color='#707070'><i>These settings define which model is selected by default when you create a brand-new session.</i></font></html>"), "gapleft 20, span, wrap");

        return panel;
    }

    private void refreshModelDropdown() {
        AgiConfig template = prefs.getAgiTemplate();
        String providerUuid = template.getSelectedProviderUuid();
        if (providerUuid == null) return;

        modelDropdown.setEnabled(false);
        modelDropdown.setToolTipText("Discovering models...");

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                AbstractAgiProvider provider = container.getProvider(providerUuid);
                if (provider == null) return new ArrayList<>();
                return provider.getModels().stream()
                        .map(AbstractModel::getModelId)
                        .toList();
            }

            @Override
            protected void done() {
                try {
                    List<String> models = get();
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                    models.forEach(model::addElement);
                    modelDropdown.setModel(model);
                    
                    if (template.getSelectedModelId() != null) {
                        modelDropdown.setSelectedItem(template.getSelectedModelId());
                    }
                    
                    // Fallback: If previous selection is invalid for the new provider, pick the first one.
                    if (modelDropdown.getSelectedIndex() == -1 && model.getSize() > 0) {
                        modelDropdown.setSelectedIndex(0);
                    }
                    
                    modelDropdown.setEnabled(true);
                    modelDropdown.setToolTipText(null);
                } catch (Exception e) {
                    log.error("Failed to discover models for preferences", e);
                    modelDropdown.setModel(new DefaultComboBoxModel<>(new String[]{"Discovery Failed"}));
                }
            }
        }.execute();
    }

    private JPanel createTemplatesTab() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // DNA Templates use the SessionConfigPanel aggregator bound to the global templates
        SessionConfigPanel templatesPanel = new SessionConfigPanel(
                prefs.getAgiTemplate(), 
                prefs.getRequestTemplate(), 
                null // No live Agi session
        );
        
        panel.add(templatesPanel, BorderLayout.CENTER);
        
        JLabel header = new JLabel("<html><div style='padding: 10px;'><b>Session DNA Templates:</b> These settings are inherited by every new session born in this container. Changes made here do not affect existing sessions.</div></html>");
        panel.add(header, BorderLayout.NORTH);
        
        return panel;
    }

    private JPanel createApiKeysTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JTabbedPane providerTabs = new JTabbedPane(JTabbedPane.LEFT);

        AgiConfig template = prefs.getAgiTemplate();
        List<String> providerUuids = template.getProviderUuids();

        for (String uuid : providerUuids) {
            AbstractAgiProvider provider = container.getProvider(uuid);
            if (provider != null) {
                ProviderKeysPanel keysPanel = new ProviderKeysPanel(provider);
                providerTabs.addTab(provider.getDisplayName(), keysPanel);
            }
        }

        panel.add(providerTabs, BorderLayout.CENTER);
        return panel;
    }

}
