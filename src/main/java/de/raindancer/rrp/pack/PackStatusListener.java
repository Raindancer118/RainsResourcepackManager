package de.raindancer.rrp.pack;

import java.util.UUID;

import de.raindancer.rrp.core.RrpService;
import de.raindancer.rrp.util.Msg;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.slf4j.Logger;

/**
 * Reports what clients actually do with the packs they are sent.
 *
 * <p>Without this, a pack that no client can download looks exactly like a pack that works: the
 * server sends it and never hears back in the log. The client does report back, so the states
 * that mean something — declined, download failed, unusable URL — are logged as warnings, and
 * the player gets one line telling them what happened rather than silently missing every custom
 * texture.
 *
 * <p>The chatty intermediate states (accepted, downloaded) are logged at debug level only.
 */
public final class PackStatusListener implements Listener {

    private final RrpService service;
    private final Logger log;

    public PackStatusListener(RrpService service, Logger log) {
        this.service = service;
        this.log = log;
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        String player = event.getPlayer().getName();
        String pack = describe(event.getID());

        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> log.info("{} loaded {}.", player, pack);
            case DECLINED -> {
                log.warn("{} declined {}.", player, pack);
                event.getPlayer().sendMessage(Msg.warn("You declined the resource pack — custom "
                        + "items will look like ordinary ones. <gray>/rrp</gray> is not needed; "
                        + "rejoin to be asked again."));
            }
            case FAILED_DOWNLOAD -> {
                log.warn("{} could not download {} — check that the URL is reachable from the "
                        + "internet and that the sha1 matches the file.", player, pack);
                event.getPlayer().sendMessage(Msg.error("The resource pack could not be "
                        + "downloaded. This is a server-side problem, not yours — it has been "
                        + "logged."));
            }
            case INVALID_URL -> log.warn("{} reported an invalid URL for {} — the client could "
                    + "not even parse it.", player, pack);
            case FAILED_RELOAD -> log.warn("{} downloaded {} but could not apply it; the pack may "
                    + "be built for a different Minecraft version.", player, pack);
            case DISCARDED -> log.debug("{} discarded {}.", player, pack);
            default -> log.debug("{} → {} for {}.", player, event.getStatus(), pack);
        }
    }

    /** Turns the pack UUID the client echoes back into something an operator recognises. */
    private String describe(UUID id) {
        if (id == null) {
            return "a resource pack";
        }
        return service.nameOfPack(id).map(name -> "'" + name + "'")
                .orElse("resource pack " + id);
    }
}
