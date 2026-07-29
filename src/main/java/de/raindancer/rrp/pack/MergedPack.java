package de.raindancer.rrp.pack;

import java.nio.file.Path;
import java.util.List;

/** The current combined pack: the file, its hash, and the URL clients can fetch it from. */
public record MergedPack(Path file, String sha1, long size, String url, List<String> packIds,
                         List<String> conflicts) {
}
