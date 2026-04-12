/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.DefaultComboBoxModel;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;
import org.jdesktop.swingx.prompt.PromptSupport;
import uno.anahata.asi.agi.provider.AbstractAgiProvider;
import uno.anahata.asi.agi.provider.TokenizerType;
import uno.anahata.asi.openai.OpenAiCompatibleProvider;
import javax.swing.JFileChooser;
import uno.anahata.asi.swing.icons.DeleteIcon;
import uno.anahata.asi.swing.icons.ExternalIcon;
import uno.anahata.asi.swing.icons.SaveIcon;
import uno.anahata.asi.swing.internal.AnyChangeDocumentListener;

/**
 * A reusable panel for managing API keys for a specific AI provider.
 * It provides a clean monospace editor for the provider's 'api_keys.txt' file
 * and a link to acquire new keys.
 * 
 * @author anahata
 */
@Slf4j
public class AiProviderPanel extends JPanel {

    /** The provider instance whose keys are being managed by this panel. */
    private final AbstractAgiProvider provider;
    /** The monospace text area for editing the raw api_keys.txt content. */
    private final JTextArea textArea;
    
    private final JTextField displayNameField;
    private final JLabel folderLabel;
    private String currentFolderName;
    private final JComboBox<TokenizerType> tokenizerCombo;
    private JTextField baseUrlField;
    private JTextField keysAcquisitionUriField;
    private JTextArea customHeadersArea;

    /**
     * Constructs a new panel for the specified provider.
     * 
     * @param provider The provider to manage keys for.
     * @param removeCallback Callback to trigger when the 'Remove' button is clicked.
     */
    public AiProviderPanel(AbstractAgiProvider provider, Runnable removeCallback) {
        super(new BorderLayout(5, 5));
        this.provider = provider;
        this.currentFolderName = provider.getFolderName();
        this.textArea = new JTextArea();
        this.textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        PromptSupport.setPrompt(provider.getApiKeyHint(), textArea);
        PromptSupport.setFocusBehavior(PromptSupport.FocusBehavior.HIDE_PROMPT, textArea);
        PromptSupport.setForeground(Color.GRAY, textArea);
        JPanel configPanel = new JPanel(new MigLayout("fillx, insets 10", "[right]10[grow,fill]5[]"));
        folderLabel = new JLabel();
        updateFolderLabel();
        configPanel.add(new JLabel("Provider Class:"));
        JTextField classField = new JTextField(provider.getClass().getName());
        classField.setEditable(false);
        classField.setBorder(null);
        classField.setOpaque(false);
        classField.setFont(classField.getFont().deriveFont(Font.ITALIC, 11f));
        configPanel.add(classField, "span 2, wrap");
        configPanel.add(new JLabel("Display Name:"));
        displayNameField = new JTextField(provider.getDisplayName());
        displayNameField.getDocument().addDocumentListener(new AnyChangeDocumentListener(() -> {
            if (currentFolderName == null || currentFolderName.isBlank()) {
                String suggested = displayNameField.getText().trim().replaceAll("[^a-zA-Z0-9.-]", "_");
                if (!suggested.isEmpty()) {
                    folderLabel.setText("<html><i>Suggested: </i><b>" + suggested + "</b></html>");
                } else {
                    updateFolderLabel();
                }
            }
        }));
        configPanel.add(displayNameField, "span 2, wrap");
        configPanel.add(new JLabel("Storage Folder:"));
        configPanel.add(folderLabel);
        JPanel folderButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton chooseFolderBtn = new JButton("Choose...");
        chooseFolderBtn.addActionListener(e-> {
            JFileChooser chooser = new JFileChooser();
            Path current = provider.getProviderDirectory();
            if (Files.exists(current)) {
                chooser.setCurrentDirectory(current.toFile());
            }
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                currentFolderName = chooser.getSelectedFile().getAbsolutePath();
                updateFolderLabel();
            }
        });
        folderButtons.add(chooseFolderBtn);
        JButton openFolderBtn = new JButton(new ExternalIcon(16));
        openFolderBtn.setToolTipText("Open Provider Folder in Desktop");
        openFolderBtn.addActionListener(e-> {
            try {
                Desktop.getDesktop().open(provider.getProviderDirectory().toFile());
            } catch (Exception ex) {
                log.error("Failed to open directory", ex);
                JOptionPane.showMessageDialog(this, "Could not open directory: " + ex.getMessage());
            }
        });
        folderButtons.add(openFolderBtn);
        configPanel.add(folderButtons, "wrap");
        configPanel.add(new JLabel("Tokenizer Type:"), "gaptop 5");
        tokenizerCombo = new JComboBox<>(TokenizerType.values());
        tokenizerCombo.setSelectedItem(provider.getTokenizerType());
        configPanel.add(tokenizerCombo, "span 2, wrap");
        configPanel.add(new JLabel("Key Acquisition URL:"));        
        keysAcquisitionUriField = new JTextField(provider.getKeysAcquisitionUri() != null ? provider.getKeysAcquisitionUri().toString() : "");
        configPanel.add(keysAcquisitionUriField, "span 2, wrap");
        if (provider instanceof OpenAiCompatibleProvider oai) {
            configPanel.add(new JLabel("Base URL:"));
            baseUrlField = new JTextField(oai.getBaseUrl());
            configPanel.add(baseUrlField, "span 2, wrap");
            configPanel.add(new JLabel("Custom Headers:"), "top");
            customHeadersArea = new JTextArea(3, 20);
            customHeadersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            if (oai.getCustomHeaders() != null) {
                String headers = oai.getCustomHeaders().entrySet().stream().map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining("\n"));
                customHeadersArea.setText(headers);
            }
            PromptSupport.setPrompt("Header-Name: Header-Value\nOne per line...", customHeadersArea);
            configPanel.add(new JScrollPane(customHeadersArea), "span 2, growx, wrap");
        }
        JCheckBox enabledCheck = new JCheckBox("Provider Enabled", provider.isEnabled());
        enabledCheck.setFont(enabledCheck.getFont().deriveFont(Font.BOLD));
        enabledCheck.addActionListener(e -> provider.setEnabled(enabledCheck.isSelected()));
        configPanel.add(enabledCheck, "span 3, wrap, gapbottom 5");
        if (provider.getKeysAcquisitionUri() != null) {
            JLabel linkLabel = new JLabel("<html><a href=''>Get " + provider.getDisplayName() + " API Keys</a></html>");
            linkLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            linkLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        Desktop.getDesktop().browse(provider.getKeysAcquisitionUri());
                    } catch (Exception ex) {
                        log.error("Failed to open acquisition URI", ex);
                    }
                }
            });
            configPanel.add(linkLabel, "span 3, wrap");
        }
        JLabel tipLabel = new JLabel("<html><font color='#707070'><i><b>Pro Tip:</b> You can add multiple keys to create a 'Key Pool'. The ASI will rotate through them in a <b>Round-Robin</b> fashion.</i></font></html>");
        configPanel.add(tipLabel, "span 3, growx, wrap, gaptop 5, gapbottom 5");
        JButton removeBtn = new JButton("Remove", new DeleteIcon(16));
        removeBtn.addActionListener(e -> removeCallback.run());
        JButton saveBtn = new JButton("Save Config & Keys", new SaveIcon(16));
        saveBtn.addActionListener(e -> saveProviderConfig());
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.add(removeBtn, BorderLayout.WEST);
        footerPanel.add(saveBtn, BorderLayout.EAST);
        add(configPanel, BorderLayout.NORTH);
        add(new JScrollPane(textArea), BorderLayout.CENTER);
        add(footerPanel, BorderLayout.SOUTH);
        loadKeys();
    }

    private void updateFolderLabel() {
        if (currentFolderName == null || currentFolderName.isBlank()) {
            folderLabel.setText("<html><i>Default (" + provider.getUuid() + ")</i></html>");
        } else {
            folderLabel.setText(currentFolderName);
            folderLabel.setToolTipText(currentFolderName);
        }
    }

    /**
     * Loads the keys from the provider's configuration file.
     */
    private void loadKeys() {
        Path path = provider.getKeysFilePath();
        try {
            if (Files.exists(path)) {
                textArea.setText(Files.readString(path));
            }
        } catch (IOException e) {
            log.error("Failed to load keys from {}", path, e);
            textArea.setText("# Error loading keys: " + e.getMessage());
        }
    }

    /**
     * Saves the current text to the provider's file and triggers a key pool reload.
     */
    private void saveProviderConfig() {
        provider.setDisplayName(displayNameField.getText().trim());
        if (currentFolderName == null || currentFolderName.isBlank()) {
            currentFolderName = displayNameField.getText().trim().replaceAll("[^a-zA-Z0-9.-]", "_");
        }
        provider.setFolderName(currentFolderName);
        updateFolderLabel();
        provider.setTokenizerType((TokenizerType) tokenizerCombo.getSelectedItem());
        provider.setKeysAcquisitionUri(keysAcquisitionUriField.getText().trim());
        if (provider instanceof OpenAiCompatibleProvider oai) {
            if (baseUrlField != null) {
                oai.setBaseUrl(baseUrlField.getText().trim());
            }
            if (customHeadersArea != null) {
                Map<String, String> headers = new HashMap<>();
                String text = customHeadersArea.getText().trim();
                if (!text.isEmpty()) {
                    for (String line : text.split("\n")) {
                        int colon = line.indexOf(":");
                        if (colon > 0) {
                            headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                        }
                    }
                }
                oai.setCustomHeaders(headers);
            }
        }
        Path path = provider.getKeysFilePath();
        try {
            Files.writeString(path, textArea.getText());
            provider.reloadKeyPool();
            JOptionPane.showMessageDialog(this, 
                    "Configuration for '" + provider.getDisplayName() + "' saved and reloaded.", 
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            log.error("Failed to save config to {}", path, e);
            JOptionPane.showMessageDialog(this, 
                    "Failed to save configuration: " + e.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
