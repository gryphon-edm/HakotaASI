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

    public void logStatus() {
        log.info("Current status: {}", testStatus);
    }
    private AtomicInteger v3Counter;

    public static class StatusMetadata {

        private final long timestamp = System.currentTimeMillis();

        public long getTimestamp() {
            return timestamp;
        }
    }
    

}
// Final end of file verification.

