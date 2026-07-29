package de.raindancer.rrp.pack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

/**
 * Persists which packs are installed, in {@code installed.yml}.
 *
 * <p>Everything RRP does to a pack is a state change here followed by a save, so a restart —
 * or a crash — never loses the fact that a pack was installed.
 */
public final class PackStore {

    private final Path file;
    private final Logger log;
    private final Map<String, InstalledPack> packs = new LinkedHashMap<>();

    public PackStore(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public synchronized void load() {
        packs.clear();
        File yaml = file.toFile();
        if (!yaml.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(yaml);
        ConfigurationSection root = config.getConfigurationSection("packs");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            packs.put(id.toLowerCase(java.util.Locale.ROOT), new InstalledPack(
                    id,
                    section.getString("name", id),
                    section.getString("url", ""),
                    section.getString("sha1", ""),
                    section.getLong("size", -1L),
                    section.getString("local-file", ""),
                    section.getBoolean("enabled", true),
                    section.getBoolean("required", false),
                    section.getInt("order", 100),
                    section.getBoolean("datapack-installed", false),
                    section.getString("datapack-file", ""),
                    section.getString("source", "unknown"),
                    section.getLong("installed-at", 0L)));
        }
        log.info("Loaded {} installed pack(s).", packs.size());
    }

    public synchronized void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().setHeader(List.of(
                "Installed resource packs, managed by Rain's Resourcepack Manager.",
                "Edit through /rrp rather than by hand — RRP rewrites this file on every change."));
        for (InstalledPack pack : packs.values()) {
            String base = "packs." + pack.id() + ".";
            config.set(base + "name", pack.name());
            config.set(base + "url", pack.url());
            config.set(base + "sha1", pack.sha1());
            config.set(base + "size", pack.size());
            config.set(base + "local-file", pack.localFile());
            config.set(base + "enabled", pack.enabled());
            config.set(base + "required", pack.required());
            config.set(base + "order", pack.order());
            config.set(base + "datapack-installed", pack.datapackInstalled());
            config.set(base + "datapack-file", pack.datapackFile());
            config.set(base + "source", pack.source());
            config.set(base + "installed-at", pack.installedAt());
        }
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            config.save(file.toFile());
        } catch (IOException e) {
            log.error("Could not write {} — the installed pack list may be lost on restart.", file, e);
        }
    }

    public synchronized Optional<InstalledPack> get(String id) {
        return Optional.ofNullable(packs.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public synchronized boolean has(String id) {
        return packs.containsKey(id.toLowerCase(java.util.Locale.ROOT));
    }

    public synchronized void put(InstalledPack pack) {
        packs.put(pack.id().toLowerCase(java.util.Locale.ROOT), pack);
        save();
    }

    public synchronized Optional<InstalledPack> remove(String id) {
        Optional<InstalledPack> removed =
                Optional.ofNullable(packs.remove(id.toLowerCase(java.util.Locale.ROOT)));
        removed.ifPresent(pack -> save());
        return removed;
    }

    /** All installed packs, in application order (lowest order value first). */
    public synchronized List<InstalledPack> all() {
        List<InstalledPack> list = new ArrayList<>(packs.values());
        list.sort(Comparator.comparingInt(InstalledPack::order).thenComparing(InstalledPack::id));
        return List.copyOf(list);
    }

    /** The enabled packs, in application order. These are what a joining player receives. */
    public synchronized List<InstalledPack> active() {
        return all().stream().filter(InstalledPack::enabled).toList();
    }

    /** The next free order value, so a newly installed pack lands at the end. */
    public synchronized int nextOrder() {
        return all().stream().mapToInt(InstalledPack::order).max().orElse(0) + 10;
    }
}
