package za.co.neroland.nerosecurity.access;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * One question for the resolver: may {@code actor} perform {@code action} against
 * {@code installation} (optionally at a specific device)? {@code server} is null in plain-JVM tests.
 */
public record AccessQuery(@Nullable MinecraftServer server, UUID actor,
        @Nullable Installation installation, AccessAction action, @Nullable DevicePos at) {
}
