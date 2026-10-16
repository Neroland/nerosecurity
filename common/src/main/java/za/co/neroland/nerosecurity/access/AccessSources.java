package za.co.neroland.nerosecurity.access;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.resources.Identifier;

/**
 * The ordered registry of {@link AccessSource}s the production resolver consults. The built-in
 * {@link MembershipSource} is always first, then the built-in {@link KeycardSource}; integrations
 * append after them and touch nothing else.
 */
public final class AccessSources {

    private static final List<AccessSource> SOURCES = new CopyOnWriteArrayList<>();

    private AccessSources() {
    }

    /** Registers the built-in sources: membership first, then keycards. Idempotent. */
    public static synchronized void init() {
        if (!contains(MembershipSource.ID)) {
            register(MembershipSource.INSTANCE);
        }
        if (!contains(KeycardSource.ID)) {
            register(KeycardSource.INSTANCE);
        }
    }

    /**
     * Appends a source.
     *
     * @throws IllegalStateException if a source with the same id is already registered
     */
    public static synchronized void register(AccessSource source) {
        if (source == null) {
            throw new IllegalArgumentException("access source must not be null");
        }
        if (contains(source.id())) {
            throw new IllegalStateException("Duplicate access source id: " + source.id());
        }
        SOURCES.add(source);
    }

    /** An unmodifiable snapshot, in evaluation order. */
    public static List<AccessSource> all() {
        return List.copyOf(SOURCES);
    }

    private static boolean contains(Identifier id) {
        for (AccessSource existing : SOURCES) {
            if (existing.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Tests only. */
    static synchronized void reset() {
        SOURCES.clear();
    }
}
