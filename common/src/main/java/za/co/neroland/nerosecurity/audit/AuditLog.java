package za.co.neroland.nerosecurity.audit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

import com.mojang.serialization.Codec;

/**
 * One installation's audit log: a capped ring buffer, oldest entry at the head. Pure (plain-JVM
 * tested); {@link SecurityAuditLog} holds one per installation and synchronises access.
 */
public final class AuditLog {

    private final ArrayDeque<AuditEntry> entries = new ArrayDeque<>();

    /** Appends {@code entry}, first evicting from the head while the log is at or above {@code cap}. */
    public void append(AuditEntry entry, int cap) {
        if (entry == null) {
            return;
        }
        int limit = Math.max(1, cap);
        while (entries.size() >= limit) {
            entries.pollFirst();
        }
        entries.addLast(entry);
    }

    /**
     * Removes every expired entry, wherever it sits (presence rows expire on the shorter clock, so they
     * are not necessarily at the head).
     *
     * @return how many entries were removed
     */
    public int purge(long now, long auditRetentionMs, long presenceRetentionMs) {
        int removed = 0;
        for (Iterator<AuditEntry> it = entries.iterator(); it.hasNext();) {
            AuditEntry entry = it.next();
            long limit = entry.isPresence() ? Math.min(presenceRetentionMs, auditRetentionMs) : auditRetentionMs;
            if (now - entry.at() > limit) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Erasure: removes every entry whose actor is {@code actor}. Returns how many. */
    public int removeActor(UUID actor) {
        if (actor == null) {
            return 0;
        }
        int before = entries.size();
        entries.removeIf(entry -> actor.equals(entry.actor()));
        return before - entries.size();
    }

    /** Export: every entry naming {@code actor}, oldest first. */
    public List<AuditEntry> byActor(UUID actor) {
        List<AuditEntry> out = new ArrayList<>();
        if (actor == null) {
            return out;
        }
        for (AuditEntry entry : entries) {
            if (actor.equals(entry.actor())) {
                out.add(entry);
            }
        }
        return out;
    }

    /** Whether any entry names {@code actor} (conformance probe). */
    public boolean hasActor(UUID actor) {
        if (actor == null) {
            return false;
        }
        for (AuditEntry entry : entries) {
            if (actor.equals(entry.actor())) {
                return true;
            }
        }
        return false;
    }

    /** Up to {@code limit} entries, newest first. */
    public List<AuditEntry> newestFirst(int limit) {
        List<AuditEntry> out = new ArrayList<>(Math.max(0, Math.min(limit, entries.size())));
        for (Iterator<AuditEntry> it = entries.descendingIterator(); it.hasNext() && out.size() < limit;) {
            out.add(it.next());
        }
        return out;
    }

    /** Every entry, oldest first. */
    List<AuditEntry> oldestFirst() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public int size() {
        return entries.size();
    }

    /** The time (epoch ms) of the oldest entry, or {@code 0} when the log is empty. */
    public long oldestAt() {
        long oldest = 0L;
        for (AuditEntry entry : entries) {
            if (oldest == 0L || entry.at() < oldest) {
                oldest = entry.at();
            }
        }
        return oldest;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The outcome of decoding a stored list: the log plus how many rows were dropped or truncated. */
    record Loaded(AuditLog log, int dropped) {
    }

    /** Builds a log from stored rows: unknown or malformed rows dropped, then the newest {@code cap} kept. */
    static Loaded fromRaw(List<AuditEntry.Raw> rows, int cap) {
        AuditLog log = new AuditLog();
        int dropped = 0;
        List<AuditEntry> valid = new ArrayList<>(rows.size());
        for (AuditEntry.Raw raw : rows) {
            Optional<AuditEntry> entry = AuditEntry.fromRaw(raw);
            if (entry.isPresent()) {
                valid.add(entry.get());
            } else {
                dropped++;
            }
        }
        int limit = Math.max(1, cap);
        int skip = Math.max(0, valid.size() - limit);
        dropped += skip;
        for (int i = skip; i < valid.size(); i++) {
            log.entries.addLast(valid.get(i));
        }
        return new Loaded(log, dropped);
    }

    List<AuditEntry.Raw> toRaw() {
        List<AuditEntry.Raw> out = new ArrayList<>(entries.size());
        for (AuditEntry entry : entries) {
            out.add(entry.toRaw());
        }
        return out;
    }

    /**
     * The list codec, oldest first: drops unknown or malformed rows and truncates to {@code cap},
     * keeping the newest.
     */
    public static Codec<AuditLog> codec(IntSupplier cap) {
        return AuditEntry.Raw.CODEC.listOf().xmap(rows -> fromRaw(rows, cap.getAsInt()).log(), AuditLog::toRaw);
    }
}
