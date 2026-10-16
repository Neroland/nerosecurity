package za.co.neroland.nerosecurity.block.field;

/** What hit a force field block and so adds to its emitter's overload meter. */
public enum LoadSource {
    /** A player hitting the block ({@code fieldMeleeLoad}). */
    MELEE,
    /** An arrow, trident, fireball or any other projectile ({@code fieldProjectileLoad}). */
    PROJECTILE,
    /** An explosion, once per explosion per emitter ({@code fieldExplosionLoadPerRadius} x radius). */
    EXPLOSION
}
