/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.standalone.swing;

import java.util.HashMap;
import java.util.Map;
import lombok.Setter;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.AgiConfig;
import uno.anahata.asi.gemini.GeminiAgiProvider;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.swing.AbstractSwingAsiContainer;
import uno.anahata.asi.swing.agi.AgiPanel;
import uno.anahata.asi.swing.agi.resources.DefaultResourceUI;
import uno.anahata.asi.swing.agi.resources.ResourceUiRegistry;

/**
 * A specialized {@link uno.anahata.asi.AbstractAsiContainer} for the standalone Swing application.
 * It manages the lifecycle of sessions within a standalone UI environment.
 * 
 * @author anahata
 */
@Slf4j
public class AsiDesktopAsiContainer extends AbstractSwingAsiContainer {
    
    static {
        log.info("Performing global Standalone environment configuration...");
        // Register the universal/standalone resource UI strategy
        ResourceUiRegistry.getInstance().setResourceUI(new DefaultResourceUI());
    }

    /** Cache of UI panels for active sessions. */
    private final Map<String, AgiPanel> agiPanels = new HashMap<>();
    
    /** Reference to the main UI panel for tab management. */
    @Setter
    private AsiDesktopMainPanel mainPanel;
    
    /**
     * Constructs a new StandaloneAsiContainer.
     */
    public AsiDesktopAsiContainer() {
        super("AsiDesktop");
        if (getProvider("Gemini") == null) {
            registerProvider(new GeminiAgiProvider());
        }
    }

    @Override
    protected void focusUI(Agi agi) {
        if (mainPanel != null) {
            mainPanel.ensureTabAndSelect(agi);
        }
    }

    @Override
    protected void closeUI(Agi agi) {
        if (mainPanel != null) {
            mainPanel.removeTab(agi);
        }
    }

    @Override
    public AgiPanel getUI(Agi agi) {
        return agiPanels.computeIfAbsent(agi.getConfig().getSessionId(), id -> {
            AgiPanel panel = new AgiPanel(agi);
            panel.setName(id);
            panel.initComponents();
            return panel;
        });
    }

    /** {@inheritDoc} */
    @Override
    public AgiConfig createNewAgiConfig() {
        return new AsiDesktopAgiConfig(this);
    }
}
