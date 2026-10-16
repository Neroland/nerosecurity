package za.co.neroland.nerosecurity.installation;

import net.minecraft.network.chat.Component;

/** The outcome of an {@link InstallationService} use case: success flag plus the message to show. */
public record ServiceResult(boolean ok, Component message) {

    public static ServiceResult success(Component message) {
        return new ServiceResult(true, message);
    }

    public static ServiceResult failure(Component message) {
        return new ServiceResult(false, message);
    }
}
