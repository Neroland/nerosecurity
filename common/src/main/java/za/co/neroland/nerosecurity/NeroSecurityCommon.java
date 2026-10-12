package za.co.neroland.nerosecurity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.economy.SecurityEconomy;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Loader-agnostic entry point for NeroSecurity. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod
 * construction. This is a barebones skeleton — no content is registered yet;
 * add shared blocks, items and systems here and reach loader-specific
 * behaviour through a platform seam.
 */
public final class NeroSecurityCommon {

    public static final String MOD_ID = "nerosecurity";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroSecurity");

    private NeroSecurityCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroSecurity] common init");

        // 0. Platform seam, resolved here during construction and never lazily on a tick path — a
        //    late ServiceLoader read can throw ServiceConfigurationError out of gameplay code.
        Services.init();

        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroSecurityConfig.init();

        // 2. Anonymous, NeroSecurity-only crash reporting. Must follow the config registration and
        //    precede the rest of init so early failures are still reported. On by default (opt-out via
        //    telemetryEnabled=false); stays inert while the build's DSN is the placeholder
        //    (see NeroSecurityTelemetry's PLACEHOLDER_DSN guard).
        NeroSecurityTelemetry.init();

        // 3. Optional NeroEconomy bridge for paid security tiers. Touches NeroEconomy only when it is loaded
        //    and new enough; claims must happen here, before the first server starts.
        SecurityEconomy.init();
    }
}
