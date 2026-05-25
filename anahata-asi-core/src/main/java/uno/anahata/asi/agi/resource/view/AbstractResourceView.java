/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.resource.view;

import lombok.Getter;
import lombok.Setter;
import uno.anahata.asi.agi.resource.Resource;

/**
 * Base implementation for resource views providing parent resource management.
 * <p>
 * This class manages the link back to the parent {@link Resource}, allowing
 * views to trigger reactive reloads when their internal configuration changes.
 * </p>
 */
public abstract class AbstractResourceView implements ResourceView {

    /**
     * The cached token count of this view's content under the active model.
     * This is marked transient to prevent serializing stale counts across
     * session restarts.
     */
    protected transient Integer tokenCount = null;
    /**
     * The parent resource orchestrator. Circularity is handled natively by
     * Kryo.
     */
    @Getter
    @Setter
    protected Resource owner;

    /**
     * {@inheritDoc}
     * <p>
     * Clears the cached token count, forcing a lazy, background recalculation
     * on the next query.
     * </p>
     */
    @Override
    public void resetTokenCount() {
        this.tokenCount = null;
    }

    /**
     * Triggers a markDirty on the owner resource to signal that the view's
     * settings have changed and need re-interpretation, and clears the cached
     * token count.
     */
    public void markDirty() {
        resetTokenCount();
        if (owner != null) {
            owner.markDirty();
        }
    }
}
