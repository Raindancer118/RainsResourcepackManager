package de.raindancer.rrp.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import de.raindancer.rrp.util.SafeFileName;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.slf4j.Logger;

/**
 * Installs the datapack half of a pack into the main world.
 *
 * <p>Most of these packs are two halves of one thing: the resource pack makes the custom disc
 * look and sound right, the datapack makes it exist. Installing only one half produces items
 * that either have no texture or cannot be obtained at all, so RRP handles both.
 *
 * <p>Only files RRP itself put there are ever deleted, and they are tracked by name in
 * {@code installed.yml} — no world data is touched.
 */
public final class DatapackService {

    private final Logger log;

    public DatapackService(Logger log) {
        this.log = log;
    }

    /**
     * @return the main world's {@code datapacks} folder — the one the server reads at startup
     *
     * <p>Deliberately built from the world container plus the level name rather than from
     * {@code World#getWorldFolder()}: on modern Paper that accessor points at the dimension
     * directory ({@code world/dimensions/minecraft/overworld}), and a datapack dropped there is
     * silently ignored.
     */
    public Path datapacksFolder() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            throw new IllegalStateException("The server has no worlds loaded yet.");
        }
        return Bukkit.getWorldContainer().toPath().resolve(world.getName()).resolve("datapacks");
    }

    /**
     * Copies {@code source} into the datapacks folder.
     *
     * @param packId used to build a stable, path-safe file name
     * @return the file name inside the datapacks folder
     */
    public String install(Path source, String packId) throws IOException {
        Path folder = datapacksFolder();
        Files.createDirectories(folder);
        String name = SafeFileName.of(packId, "pack") + "-datapack.zip";
        Path target = folder.resolve(name);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Installed datapack {}", target);
        return name;
    }

    /**
     * Removes a datapack RRP installed earlier.
     *
     * @return true when a file was actually deleted
     */
    public boolean remove(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String safe = SafeFileName.of(fileName, "");
        if (safe.isBlank()) {
            return false;
        }
        Path target = datapacksFolder().resolve(safe);
        boolean deleted = Files.deleteIfExists(target);
        if (deleted) {
            log.info("Removed datapack {}", target);
        }
        return deleted;
    }

    /**
     * Reloads the server's data packs — the API equivalent of {@code /minecraft:reload}.
     *
     * <p>Main thread only. Recipes, advancements, tags and datapack registries come back; the
     * running world is not reloaded.
     */
    public void reloadData() {
        Bukkit.reloadData();
    }
}
