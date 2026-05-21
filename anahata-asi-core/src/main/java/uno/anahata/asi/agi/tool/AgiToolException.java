/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool;

/**
 * A custom runtime exception that can be thrown by AI tools to provide a concise,
 * user-friendly error message to the model without including a stack trace.
 * <p>
 * Using a RuntimeException simplifies tool signatures by removing the need for 
 * checked exception declarations.
 * </p>
 *
 * @author anahata
 */
public class AgiToolException extends RuntimeException {

    /**
     * Constructs a new AgiToolException with a specific error message.
     * 
     * @param message The user-friendly error message.
     */
    public AgiToolException(String message) {
        super(message);
    }

    /**
     * Constructs a new AgiToolException with a specific error message and cause.
     * 
     * @param message The user-friendly error message.
     * @param cause The underlying cause of the exception.
     */
    public AgiToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
