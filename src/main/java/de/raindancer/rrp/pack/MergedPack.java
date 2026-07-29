package de.raindancer.rrp.pack;

import java.nio.file.Path;
import java.util.List;

/**
 * The current combined pack: its hash, the URL clients fetch it from, and — only when this
 * server built it itself — the local file.
 *
 * @param file null when the pack host built the combination, which is the normal case
 */
public record MergedPack(Path file, String sha1, long size, String url, List<String> packIds,
                         List<String> conflicts, boolean remote) {

    public static MergedPack local(Path file, String sha1, long size, String url,
                                   List<String> packIds, List<String> conflicts) {
        return new MergedPack(file, sha1, size, url, packIds, conflicts, false);
    }

    public static MergedPack remote(String sha1, long size, String url, List<String> packIds,
                                    List<String> conflicts) {
        return new MergedPack(null, sha1, size, url, packIds, conflicts, true);
    }
}
