package za.co.neroland.nerosecurity.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.event.EventPhrases;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.Installation;

/** 24. Who gets the alarm chat line, and that the line names the installation and the event only. */
class AlarmChatRecipientsTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID CO_OWNER = UUID.randomUUID();
    private static final UUID OFFICER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID INTRUDER = UUID.randomUUID();

    private static Map<UUID, AccessLevel> members() {
        Map<UUID, AccessLevel> m = new LinkedHashMap<>();
        m.put(OFFICER, AccessLevel.OFFICER);
        m.put(MEMBER, AccessLevel.MEMBER);
        return m;
    }

    @Test
    void ownersAlwaysOfficersOnlyWhenFlagged() {
        Set<UUID> owners = Set.of(OWNER, CO_OWNER, Installation.TOMBSTONE_OWNER);
        Set<UUID> quiet = AlarmChat.recipients(owners, members(), false);
        assertEquals(Set.of(OWNER, CO_OWNER), quiet, "owners only, never the tombstone");
        Set<UUID> loud = AlarmChat.recipients(owners, members(), true);
        assertEquals(Set.of(OWNER, CO_OWNER, OFFICER), loud, "officers join only with notifyOfficers");
        assertFalse(loud.contains(MEMBER), "members never receive alarm chat");
    }

    @Test
    void installationOverloadUsesTheStoredRows() {
        Installation installation = new Installation(UUID.randomUUID(), OWNER, "A", 0L, new byte[32]);
        assertEquals(Set.of(OWNER), AlarmChat.recipients(installation), "notifyOfficers defaults to false");
    }

    @Test
    void onlineOnlyByUuid() {
        Set<UUID> recipients = AlarmChat.recipients(Set.of(OWNER, CO_OWNER), members(), true);
        List<UUID> asked = new ArrayList<>();
        List<String> delivered = AlarmChat.online(recipients, id -> {
            asked.add(id);
            return id.equals(CO_OWNER) ? null : "player-" + id; // CO_OWNER is offline
        });
        assertEquals(recipients.size(), asked.size(), "each recipient is looked up once, by UUID");
        assertTrue(recipients.containsAll(asked), "nobody but a recipient is ever looked up");
        assertEquals(Set.of("player-" + OWNER, "player-" + OFFICER), new HashSet<>(delivered),
                "offline players simply get nothing");
    }

    @Test
    void messageNamesInstallationAndPhraseOnly() {
        AlarmFiring firing = new AlarmFiring(SecurityEventType.LASER_TRIPPED, SecuritySeverity.CRITICAL, 3,
                Set.of(SecurityEventType.LASER_TRIPPED));
        Component line = AlarmChat.message("Orbital Lab", firing);
        TranslatableContents root = assertInstanceOf(TranslatableContents.class, line.getContents());
        assertEquals("alarm.nerosecurity.chat", root.getKey());
        Object[] args = root.getArgs();
        assertEquals(3, args.length);
        assertEquals("Orbital Lab", literal(args[0]));
        TranslatableContents phrase = assertInstanceOf(TranslatableContents.class, ((Component) args[1]).getContents());
        assertEquals(EventPhrases.key(SecurityEventType.LASER_TRIPPED), phrase.getKey());
        assertEquals(0, phrase.getArgs().length, "phrases take no argument, so no actor can be rendered in");
        TranslatableContents more = assertInstanceOf(TranslatableContents.class, ((Component) args[2]).getContents());
        assertEquals("alarm.nerosecurity.more", more.getKey());
        assertEquals(List.of(3), List.of(more.getArgs()));

        String everything = flatten(line);
        for (UUID id : List.of(OWNER, OFFICER, MEMBER, INTRUDER)) {
            assertFalse(everything.contains(id.toString()), "no UUID ever reaches chat");
        }

        Component single = AlarmChat.message("Orbital Lab", AlarmFiring.single(SecurityEventType.MOTION_DETECTED,
                SecuritySeverity.WARN));
        Object[] singleArgs = ((TranslatableContents) single.getContents()).getArgs();
        assertEquals("", literal(singleArgs[2]), "no '+N more' on a leading-edge firing");
    }

    private static String literal(Object arg) {
        Component c = (Component) arg;
        return assertInstanceOf(PlainTextContents.class, c.getContents()).text();
    }

    /** Every key, literal and argument in the tree, without resolving any language. */
    private static String flatten(Component c) {
        StringBuilder out = new StringBuilder();
        if (c.getContents() instanceof TranslatableContents t) {
            out.append(t.getKey()).append('|');
            for (Object arg : t.getArgs()) {
                out.append(arg instanceof Component nested ? flatten(nested) : String.valueOf(arg)).append('|');
            }
        } else if (c.getContents() instanceof PlainTextContents p) {
            out.append(p.text()).append('|');
        }
        for (Component sibling : c.getSiblings()) {
            out.append(flatten(sibling));
        }
        return out.toString();
    }
}
