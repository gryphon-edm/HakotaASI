/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.java.coderefiner;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Anahata ASI
 */
@Slf4j
public class SmallTestClass {
    
    private String testStatus;
    private AtomicLong testCounter;

    /**
     * Logs the current ASI status.
     */
    public void logStatus() {
        log.info("ASI Status: {}", testStatus);
    }
    
    private AtomicInteger v3Counter;
    
    public static class AnotherInnerClass {
        private AtomicInteger messiGoat;
    }

    public static class StatusMetadata {

        private final long timestamp = System.currentTimeMillis();

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * @author Anahata ASI
     */
    @Slf4j
    public static class BigTestClass {

        /**
         * test
         */
        private String testField;
        private SmallTestClass smallTestClass;

        public static class StatusMetadata {

            private final long timestamp = System.currentTimeMillis();

            public long getTimestamp() {
                return timestamp;
            }
        }
    }
    // Final end of file verification.
    

}
// Final end of file verification.
