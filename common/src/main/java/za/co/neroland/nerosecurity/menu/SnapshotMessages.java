package za.co.neroland.nerosecurity.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import org.jetbrains.annotations.Nullable;

/**
 * The {@code (boolean ok, @Nullable Component message)} tail every settings snapshot carries: the outcome of the
 * action that produced the snapshot, server-authored, for the screen's footer (green when {@code ok}, red for a
 * refusal; no message = nothing to say). The message is a server-built component (lang keys and level names),
 * never client text.
 */
public final class SnapshotMessages {

    private SnapshotMessages() {
    }

    public static void write(RegistryFriendlyByteBuf buf, boolean ok, @Nullable Component message) {
        buf.writeBoolean(ok);
        buf.writeBoolean(message != null);
        if (message != null) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, message);
        }
    }

    public static boolean readOk(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean();
    }

    public static @Nullable Component readMessage(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf) : null;
    }
}
