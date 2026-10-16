package za.co.neroland.nerosecurity.client.widget;

import java.util.Objects;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * Base of every interactive NeroSecurity widget. It owns the one rule the "no manual" test checks hardest:
 * <b>a disabled control always says why</b>.
 *
 * <ul>
 *   <li>{@link #disable(Component)} takes the reason, shown as the tooltip while disabled. There is no way to
 *       disable a widget without a reason.</li>
 *   <li>{@link #setHelp(Component)} is the tooltip while enabled (optional).</li>
 *   <li>The screen hull <em>holds</em> intent widgets while it waits for the server's answer
 *       ({@code SecurityScreen.awaitSnapshot()}); a held widget is inactive and its tooltip says
 *       "Waiting for the server…". {@link #setIntent(boolean) setIntent(false)} exempts navigation widgets
 *       (tabs, close, scroll) from holding.</li>
 * </ul>
 *
 * <p>Widgets are display-only. They never decide access: the screen mirrors the snapshot's
 * {@code viewerLevel}/{@code admin} into enable/disable calls, and the server re-checks every payload.
 *
 * <p>Coordinates are absolute screen pixels (pass {@code leftPos + dx}, {@code topPos + dy}). CLIENT-ONLY.
 */
public abstract class SecurityWidget extends AbstractWidget {

    /** Used only if a caller passes {@code null} as a reason; callers must always pass a real one. */
    private static final Component FALLBACK_WHY = Component.translatable("ui.nerosecurity.why.unavailable");

    private boolean enabled = true;
    private @Nullable Component why;
    private @Nullable Component help;
    private boolean intent = true;
    private boolean held;
    private @Nullable Component heldWhy;
    /** The tooltip text currently installed, so the Tooltip object is rebuilt only on change. */
    private @Nullable Component shownTip;

    protected SecurityWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    // --- enable / disable with a reason ---------------------------------------------------------

    /** Enables the widget (subject to the hull's hold while waiting for the server). */
    public final void enable() {
        this.enabled = true;
        this.why = null;
        sync();
    }

    /**
     * Disables the widget and shows {@code why} as its tooltip. Use the same words as the server's deny
     * messages, e.g. {@code door.nerosecurity.owner_only_setting}.
     */
    public final void disable(Component why) {
        this.enabled = false;
        this.why = why == null ? FALLBACK_WHY : why;
        sync();
    }

    /** {@code enabled ? enable() : disable(whyIfDisabled)}. */
    public final void setEnabled(boolean enabled, Component whyIfDisabled) {
        if (enabled) {
            enable();
        } else {
            disable(whyIfDisabled);
        }
    }

    /** Whether the screen has enabled this widget (ignores the hull's hold). */
    public final boolean isEnabledByScreen() {
        return enabled;
    }

    /** The reason shown while disabled, or {@code null} when enabled. */
    public final @Nullable Component why() {
        return enabled ? null : why;
    }

    /** The tooltip shown while enabled; {@code null} for none. */
    public final void setHelp(@Nullable Component help) {
        this.help = help;
        sync();
    }

    public final @Nullable Component help() {
        return help;
    }

    // --- hull hold ------------------------------------------------------------------------------

    /** {@code false} for navigation widgets the hull must not hold while waiting (tabs, close, scroll). */
    public final void setIntent(boolean intent) {
        this.intent = intent;
        if (!intent && held) {
            setHeld(false, null);
        }
    }

    public final boolean isIntent() {
        return intent;
    }

    /** Called by the screen hull only. */
    public final void setHeld(boolean held, @Nullable Component why) {
        boolean value = held && intent;
        if (value == this.held && Objects.equals(why, this.heldWhy)) {
            return;
        }
        this.held = value;
        this.heldWhy = why;
        sync();
    }

    public final boolean isHeld() {
        return held;
    }

    /** Called every client tick by the hull; override for timed behaviour (repeat, debounce). */
    public void tick() {
    }

    /** Re-derives {@code active} and the tooltip from the enabled / held / help state. */
    protected final void sync() {
        this.active = enabled && !held;
        Component tip;
        if (!enabled) {
            tip = why;
        } else if (held) {
            tip = heldWhy;
        } else {
            tip = help;
        }
        if (!Objects.equals(tip, shownTip)) {
            shownTip = tip;
            setTooltip(tip == null ? null : Tooltip.create(tip));
        }
    }

    /** {@code true} when the mouse is over (or keyboard focus is on) an active widget. */
    protected final boolean hot() {
        return this.active && isHoveredOrFocused();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
