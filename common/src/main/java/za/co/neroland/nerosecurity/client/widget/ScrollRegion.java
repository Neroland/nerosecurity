package za.co.neroland.nerosecurity.client.widget;

/**
 * Something that scrolls under the mouse wheel (a {@link SentenceList}, a screen's member list). Register it with
 * {@code SecurityScreen.addScrollRegion}; the hull offers every wheel event to each region in turn.
 * CLIENT-ONLY.
 */
public interface ScrollRegion {

    /** Scrolls if the mouse is over the region; {@code true} when the event was used. */
    boolean mouseScrolled(double mouseX, double mouseY, double scrollY);
}
