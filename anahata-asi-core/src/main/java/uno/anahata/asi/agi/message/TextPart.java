/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.message;

import lombok.Getter;
import lombok.Setter;
import uno.anahata.asi.agi.provider.AbstractModel;

/**
 * An abstract base class for text-based message parts.
 * It supports reactive UI updates by firing property change events when the text is modified.
 *
 * @author anahata-gemini-pro-2.5
 */
@Getter
@Setter
public abstract class TextPart extends AbstractPart {
    
    /** The text content of this part. */
    private String text;

    /**
     * Constructs a new TextPart.
     *
     * @param message The parent message.
     * @param text The initial text content.
     */
    public TextPart(AbstractMessage message, String text) {
        super(message);
        setText(text);
    }
    
    /**
     * Sets the text content and fires a property change event for the "text" property.
     * Also updates the token count using the active session tokenizer.
     * @param text The new text content to apply.
     */
    public void setText(java.lang.String text) {
        String oldText = this.text;
        this.text = text;
        resetTokenCount();
        propertyChangeSupport.firePropertyChange("text", oldText, text);
    }

    /**
     * Appends text to the existing content and fires a property change event.
     * Also updates the token count using the active session tokenizer.
     * @param delta The text to append.
     */
    public void appendText(java.lang.String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        String oldText = this.text;
        this.text = (this.text == null ? "" : this.text) + delta;
        resetTokenCount();
        propertyChangeSupport.firePropertyChange("text", oldText, this.text);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Re-calculates the text token count using the currently selected model.
     * </p>
     */
    @Override protected void calculateTokenCount() {
        AbstractModel model = getAgi() != null ? getAgi().getSelectedModel() : null;
        setTokenCount(model != null ? model.countTokens(text) : 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void resetTokenCount() {
        super.resetTokenCount();
    }
    /** {@inheritDoc} */
    @Override
    public String asText() {
        return text;
    }

    /**
     * {@inheritDoc}
     * Returns the default maximum depth to keep a text part in context,
     * as defined in the agi configuration.
     */
    @Override
    protected int getDefaultMaxDepth() {
        return getAgiConfig().getDefaultTextPartMaxDepth();
    }

    @Override
    protected void appendMetadata(StringBuilder sb) {
        if (isThought()){
            sb.append(" | thought "); 
        }
    }
    
    
    
    /**
     * Indicates if this text part represents a model thought process.
     * For a generic TextPart, this is always false.
     * @return always false
     */
    public boolean isThought() {
        return false;
    }
}
