package za.co.neroland.nerosecurity.alarm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.EventPhrases;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The alarm's chat line: {@code "[<installation>] <event phrase> (+N more)"}, to the installation's
 * owners and, when the installation asks for it, its officers.
 *
 * <ul>
 *   <li><b>No identity, ever.</b> The line is built from the owner-chosen installation label and the
 *       fixed {@link EventPhrases} table only. No method here takes an actor, so no intruder's name or
 *       UUID can reach chat, not even the owner's own.</li>
 *   <li><b>Recipients by UUID, online only.</b> Each recipient UUID is looked up in the server's online
 *       player list; anyone offline simply gets nothing (nothing is queued). Never by proximity.</li>
 * </ul>
 */
public final class AlarmChat {

    private AlarmChat() {
    }

    /** Owners (minus the erasure tombstone), plus officers when {@code installation.notifyOfficers()}. */
    public static Set<UUID> recipients(Installation installation) {
        if (installation == null) {
            return Set.of();
        }
        return recipients(installation.owners(), installation.members(), installation.notifyOfficers());
    }

    /** Pure form of {@link #recipients(Installation)}: members below OFFICER never receive anything. */
    public static Set<UUID> recipients(Collection<UUID> owners, Map<UUID, AccessLevel> members, boolean notifyOfficers) {
        Set<UUID> out = new LinkedHashSet<>();
        if (owners != null) {
            for (UUID owner : owners) {
                if (owner != null && !Installation.TOMBSTONE_OWNER.equals(owner)) {
                    out.add(owner);
                }
            }
        }
        if (notifyOfficers && members != null) {
            for (Map.Entry<UUID, AccessLevel> row : members.entrySet()) {
                if (row.getKey() != null && row.getValue() == AccessLevel.OFFICER
                        && !Installation.TOMBSTONE_OWNER.equals(row.getKey())) {
                    out.add(row.getKey());
                }
            }
        }
        return out;
    }

    /** Each recipient UUID through {@code online}; null answers (offline players) are skipped. */
    public static <P> List<P> online(Collection<UUID> recipients, Function<UUID, P> online) {
        List<P> out = new ArrayList<>();
        for (UUID id : recipients) {
            P player = online.apply(id);
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }

    /** The chat line. Takes the installation label and the firing only: there is no actor to leak. */
    public static Component message(String installationName, AlarmFiring firing) {
        Component more = firing.extra() > 0
                ? Component.translatable("alarm.nerosecurity.more", firing.extra())
                : Component.literal("");
        MutableComponent line = Component.translatable("alarm.nerosecurity.chat",
                Component.literal(installationName == null ? "" : installationName),
                EventPhrases.phrase(firing.type()), more);
        return line.withStyle(colour(firing.severity()));
    }

    static ChatFormatting colour(SecuritySeverity severity) {
        return switch (severity) {
            case CRITICAL -> ChatFormatting.RED;
            case WARN -> ChatFormatting.GOLD;
            case INFO -> ChatFormatting.GRAY;
        };
    }

    /** Sends the line to every online recipient. Does nothing while alarm chat is off on the server. */
    public static void send(MinecraftServer server, Installation installation, AlarmFiring firing) {
        if (server == null || installation == null || firing == null || !NeroSecurityConfig.isAlarmChatEnabled()) {
            return;
        }
        Component line = message(installation.name(), firing);
        for (ServerPlayer player : online(recipients(installation), id -> server.getPlayerList().getPlayer(id))) {
            player.sendSystemMessage(line);
        }
    }
}
