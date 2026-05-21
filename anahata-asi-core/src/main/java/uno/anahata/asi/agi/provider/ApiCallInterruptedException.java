/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.provider;

/**
 * Exception thrown when an AI model generation call is interrupted, either
 * by the user or by the system.
 * 
 * @author anahata
 */
public class ApiCallInterruptedException extends RuntimeException {

    /**
     * Constructs a new ApiCallInterruptedException with a specific error message.
     * 
     * @param message The details of the interruption.
     */
    public ApiCallInterruptedException(String message) {
        super(message);
    }

    /**
     * Constructs a new ApiCallInterruptedException with a specific error message and cause.
     * 
     * @param message The details of the interruption.
     * @param cause The underlying cause of the interruption.
     */
    public ApiCallInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ApiCallInterruptedException with a specific underlying cause.
     * 
     * @param cause The underlying cause of the interruption.
     */
    public ApiCallInterruptedException(Throwable cause) {
        super(cause);
    }
}
