/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.swing.agi.message.part.tool.param;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.agi.tool.spi.AbstractToolCall;
import uno.anahata.asi.swing.agi.AgiPanel;
import uno.anahata.asi.swing.agi.resources.ResourceUiRegistry;
import uno.anahata.asi.swing.icons.ExternalIcon;
import uno.anahata.asi.swing.components.WrapLayout;

/**
 * A specialized parameter renderer for URIs.
 * <p>
 * This renderer provides a sleek chip showing the URI and allows 
 * opening it via the host-specific ResourceUI strategy.
 * </p>
 * 
 * @author anahata
 */
@Slf4j
public class UriParameterRenderer implements ParameterRenderer<Object> {

    private AgiPanel agiPanel;
    private AbstractToolCall<?, ?> call;
    private String paramName;
    private Object value;

    private final JPanel container = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 5));

    public UriParameterRenderer() {
        container.setOpaque(false);
    }

    @Override
    public void init(AgiPanel agiPanel, AbstractToolCall<?, ?> call, String paramName, Object value) {
        this.agiPanel = agiPanel;
        this.call = call;
        this.paramName = paramName;
        updateContent(value);
    }

    @Override
    public JComponent getComponent() {
        return container;
    }

    @Override
    public void updateContent(Object value) {
        this.value = value;
    }

    @Override
    public boolean render() {
        container.removeAll();

        if (value == null || (value instanceof String s && s.isBlank())) {
            container.add(new JLabel("null"));
            return true;
        }

        if (value instanceof java.util.List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    container.add(createChip(item.toString()));
                }
            }
        } else {
            container.add(createChip(value.toString()));
        }

        container.revalidate();
        container.repaint();
        return true;
    }

    private JPanel createChip(String uriString) {
        JPanel chip = new JPanel(new BorderLayout(10, 0));
        chip.setOpaque(true);
        chip.setBackground(new java.awt.Color(230, 235, 245));
        chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(180, 200, 220), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        // Use the last part of the URI as display name for cleanliness
        String displayName = uriString;
        try {
            int lastSlash = uriString.lastIndexOf('/');
            if (lastSlash != -1 && lastSlash < uriString.length() - 1) {
                displayName = uriString.substring(lastSlash + 1);
            }
        } catch (Exception e) {}

        JLabel label = new JLabel(displayName);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setToolTipText(uriString);
        chip.add(label, BorderLayout.CENTER);

        JButton openBtn = new JButton(new ExternalIcon(14));
        openBtn.setToolTipText("Open URI");
        openBtn.setMargin(new java.awt.Insets(0, 2, 0, 2));
        openBtn.setContentAreaFilled(false);
        openBtn.setBorderPainted(false);
        openBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        openBtn.addActionListener(e -> {
            if (ResourceUiRegistry.getInstance().getResourceUI() != null) {
                ResourceUiRegistry.getInstance().getResourceUI().openUri(uriString);
            }
        });
        
        chip.add(openBtn, BorderLayout.EAST);
        return chip;
    }
}