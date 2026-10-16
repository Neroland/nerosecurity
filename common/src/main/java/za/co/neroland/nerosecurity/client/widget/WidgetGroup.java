package za.co.neroland.nerosecurity.client.widget;

import java.util.List;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * A widget made of several child widgets ({@link LevelLadder}, {@link Segmented}, {@link TabBar}). Add it with
 * {@code SecurityScreen.addGroup(group)}, which registers every child. CLIENT-ONLY.
 */
public interface WidgetGroup {

    /** The children, in tab order. */
    List<? extends AbstractWidget> widgets();
}
