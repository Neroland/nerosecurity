package za.co.neroland.nerosecurity.platform;

import java.util.ServiceLoader;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * Loads loader-specific platform-seam implementations via {@link ServiceLoader}.
 *
 * <p>Common code calls {@code Services.PLATFORM.xxx()}; the correct Fabric / Forge / NeoForge
 * implementation is resolved at runtime from the {@code META-INF/services} entry each loader module
 * ships. The seam is resolved during class initialisation, which happens the first time
 * {@code NeroSecurityCommon.init()} touches this class — <b>never lazily mid-tick</b>. A lazy
 * {@link ServiceLoader} read can throw {@code ServiceConfigurationError} out of gameplay code if
 * the jar has become unreadable (Nerospace crash precedent MC-NEROSPACE-F).
 */
public final class Services {

    public static final PlatformInfo PLATFORM = load(PlatformInfo.class);

    private Services() {
    }

    /** Resolves the seam now, so nothing has to be resolved on a tick path later. */
    public static void init() {
        NeroSecurityCommon.LOGGER.debug("[NeroSecurity] platform seams resolved ({})",
                PLATFORM.getPlatformName());
    }

    public static <T> T load(Class<T> clazz) {
        final T loaded = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException(
                        "No implementation found for service " + clazz.getName()));
        NeroSecurityCommon.LOGGER.debug("Loaded service {} -> {}",
                clazz.getSimpleName(), loaded.getClass().getName());
        return loaded;
    }
}
