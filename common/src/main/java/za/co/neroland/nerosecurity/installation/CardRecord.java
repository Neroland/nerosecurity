package za.co.neroland.nerosecurity.installation;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;

/**
 * One row of an installation's card ledger: what the server issued under one serial. A card whose
 * serial has no row is dead, which is how revocation works.
 *
 * @param boundOwner the only player who may present a bound card; null for an unbound card. This is
 *                   the one place a card's holder is recorded, so erasure can kill a bound card by
 *                   deleting its row.
 * @param issuedAt   epoch ms
 */
public record CardRecord(AccessLevel level, boolean encrypted, @Nullable UUID boundOwner, long issuedAt) {
}
