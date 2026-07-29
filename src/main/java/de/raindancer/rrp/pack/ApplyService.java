package de.raindancer.rrp.pack;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import de.raindancer.rrp.core.RrpConfig;
import de.raindancer.rrp.util.Hashes;
import de.raindancer.rrp.util.Msg;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

/**
 * Sends the installed packs to players.
 *
 * <p>Two ways to do that, both fully supported by the vanilla client since 1.20.3:
 *
 * <ul>
 *   <li><b>stacked</b> — every pack is sent as its own entry and the client applies them in
 *       order. No server-side work, but the player sees one prompt per pack.</li>
 *   <li><b>combined</b> — one merged zip, one prompt. Needs a URL clients can reach, which is
 *       what RRP's own HTTP server is for.</li>
 * </ul>
 *
 * <p>"Required" is a property of a request, not of a single pack, so packs are grouped: the
 * required ones go out in one request that replaces whatever the client had, the optional ones
 * follow in a second request that adds to it.
 */
public final class ApplyService {

    /** What actually happened, so the caller can report it truthfully. */
    public record Result(int packsSent, boolean combined, boolean required, String reason) {
        public static Result none(String reason) {
            return new Result(0, false, false, reason);
        }
    }

    private final PackStore store;
    private final Supplier<RrpConfig> config;
    private final Supplier<Optional<MergedPack>> merged;
    private final Logger log;

    public ApplyService(PackStore store, Supplier<RrpConfig> config,
                        Supplier<Optional<MergedPack>> merged, Logger log) {
        this.store = store;
        this.config = config;
        this.merged = merged;
        this.log = log;
    }

    /** Sends every enabled pack to one player. Main thread. */
    public Result apply(Player player) {
        List<InstalledPack> active = store.active();
        if (active.isEmpty()) {
            player.clearResourcePacks();
            return Result.none("no packs are enabled");
        }

        RrpConfig conf = config.get();
        Component prompt = prompt(conf);

        if (wantsCombined(conf, active)) {
            Optional<MergedPack> combined = merged.get();
            if (combined.isPresent() && !combined.get().url().isBlank()) {
                MergedPack pack = combined.get();
                Optional<ResourcePackInfo> info = info("combined", pack.url(), pack.sha1());
                if (info.isPresent()) {
                    boolean required = active.stream().anyMatch(InstalledPack::required);
                    player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                            .packs(info.get())
                            .prompt(prompt)
                            .required(required)
                            .replace(true)
                            .build());
                    return new Result(active.size(), true, required, "combined pack");
                }
            }
            if (conf.mode() == RrpConfig.ApplyMode.MERGED) {
                log.warn("apply.mode is 'merged' but no combined pack is available — "
                        + "sending the packs stacked instead. Run /rrp merge and check http.*.");
            }
        }

        return stacked(player, active, prompt);
    }

    /** Sends the packs to everyone online. */
    public Result applyAll(Iterable<? extends Player> players) {
        Result last = Result.none("nobody online");
        for (Player player : players) {
            last = apply(player);
        }
        return last;
    }

    /** Tells the client to drop every pack RRP sent it. */
    public void clear(Player player) {
        player.clearResourcePacks();
    }

    /** Removes a single pack from a client without touching the others. */
    public void remove(Player player, InstalledPack pack) {
        player.removeResourcePacks(Hashes.packId(pack.id(), pack.sha1()));
    }

    // --- internals -------------------------------------------------------------------------

    private boolean wantsCombined(RrpConfig conf, List<InstalledPack> active) {
        return switch (conf.mode()) {
            case STACKED -> false;
            case MERGED -> true;
            // Combining a single pack would only add a rewrite step and a second hash.
            case AUTO -> active.size() > 1;
        };
    }

    private Result stacked(Player player, List<InstalledPack> active, Component prompt) {
        List<ResourcePackInfo> required = new ArrayList<>();
        List<ResourcePackInfo> optional = new ArrayList<>();
        List<String> broken = new ArrayList<>();

        for (InstalledPack pack : active) {
            Optional<ResourcePackInfo> info = info(pack.id(), pack.url(), pack.sha1());
            if (info.isEmpty()) {
                broken.add(pack.id());
                continue;
            }
            (pack.required() ? required : optional).add(info.get());
        }
        if (!broken.isEmpty()) {
            log.warn("Skipped pack(s) with an unusable URL: {}", String.join(", ", broken));
        }
        if (required.isEmpty() && optional.isEmpty()) {
            return Result.none("no pack had a usable URL");
        }

        if (!required.isEmpty()) {
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(required)
                    .prompt(prompt)
                    .required(true)
                    .replace(true)
                    .build());
        }
        if (!optional.isEmpty()) {
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(optional)
                    .prompt(prompt)
                    .required(false)
                    // Only replace when this is the first request, otherwise the required packs
                    // sent a moment ago would be dropped again.
                    .replace(required.isEmpty())
                    .build());
        }
        return new Result(required.size() + optional.size(), false, !required.isEmpty(), "stacked");
    }

    private Optional<ResourcePackInfo> info(String id, String url, String sha1) {
        try {
            UUID uuid = Hashes.packId(id, sha1 == null ? "" : sha1);
            URI uri = new URI(url);
            ResourcePackInfo.Builder builder = ResourcePackInfo.resourcePackInfo()
                    .id(uuid)
                    .uri(uri);
            if (sha1 != null && !sha1.isBlank()) {
                builder.hash(sha1);
            }
            return Optional.of(builder.build());
        } catch (URISyntaxException | IllegalArgumentException e) {
            log.warn("Pack '{}' has an unusable URL '{}': {}", id, url, e.getMessage());
            return Optional.empty();
        }
    }

    private static Component prompt(RrpConfig conf) {
        String text = conf.prompt();
        return text == null || text.isBlank() ? Component.empty() : Msg.raw(text);
    }
}
