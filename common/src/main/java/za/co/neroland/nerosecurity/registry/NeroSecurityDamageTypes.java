package za.co.neroland.nerosecurity.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * NeroSecurity's data-driven damage types ({@code data/nerosecurity/damage_type/}). Use with
 * {@code level.damageSources().source(NeroSecurityDamageTypes.LASER)}; the death message key is
 * {@code death.attack.nerosecurity.laser}.
 */
public final class NeroSecurityDamageTypes {

    /** A laser grid beam (only when the server sets {@code laserTripDamage} above 0). */
    public static final ResourceKey<DamageType> LASER = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "laser"));

    private NeroSecurityDamageTypes() {
    }
}
