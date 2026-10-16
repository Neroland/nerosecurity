package za.co.neroland.nerosecurity.access;

import net.minecraft.resources.Identifier;

/**
 * A pluggable voter in the access decision. Sources are evaluated in registration order; the
 * outcome does not depend on that order (deny wins, highest grant wins), only which deny is quoted.
 *
 * <p>Contract: runs on the server thread; must never throw (a throwing source is treated as a
 * deny); must never authorise by proximity (no nearest-player lookups of any kind).
 */
public interface AccessSource {

    /** Stable id, e.g. {@code nerosecurity:membership}. Its display key is {@code access.<ns>.source.<path>}. */
    Identifier id();

    AccessVote vote(AccessQuery query);
}
