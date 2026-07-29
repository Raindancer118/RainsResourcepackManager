package de.raindancer.rrp.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.raindancer.rrp.RrpPlugin;
import de.raindancer.rrp.catalog.CatalogItem;
import de.raindancer.rrp.catalog.CatalogPack;
import de.raindancer.rrp.core.RrpConfig;
import de.raindancer.rrp.core.RrpService;
import de.raindancer.rrp.give.GiveService;
import de.raindancer.rrp.gui.MainMenu;
import de.raindancer.rrp.gui.MenuManager;
import de.raindancer.rrp.pack.ApplyService;
import de.raindancer.rrp.pack.InstalledPack;
import de.raindancer.rrp.util.Msg;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The {@code /rrp} command tree.
 *
 * <p>A Paper {@link BasicCommand} registered through the lifecycle API — the modern replacement
 * for {@code onCommand} plus a {@code commands:} block. Every subcommand maps onto exactly one
 * {@link RrpService} method, which is the same set of methods the GUI drives, so the two front
 * ends cannot drift apart.
 *
 * <p>Tab completion is contextual all the way down: pack ids come from what is installed or from
 * the catalogue depending on the subcommand, item references come from the catalogue, and player
 * names from who is online.
 */
public final class RrpCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of(
            "gui", "list", "catalog", "info", "install", "uninstall", "enable", "disable",
            "required", "move", "mode", "merge", "apply", "datapack", "give", "set",
            "reload", "help");

    private final RrpPlugin plugin;
    private final RrpService service;
    private final MenuManager menus;

    public RrpCommand(RrpPlugin plugin, RrpService service, MenuManager menus) {
        this.plugin = plugin;
        this.service = service;
        this.menus = menus;
    }

    @Override
    public String permission() {
        // The give-only permission is checked per subcommand; this gate must let those users in.
        return null;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return plugin.hasAdminPermission(sender) || plugin.hasGivePermission(sender);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            if (plugin.hasAdminPermission(sender) && sender instanceof Player player) {
                menus.open(player, new MainMenu(service, menus));
            } else {
                help(sender);
            }
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        // Everyone with rrp.give may hand out items; everything else is admin only.
        if (sub.equals("give")) {
            if (!plugin.hasGivePermission(sender)) {
                sender.sendMessage(RrpPlugin.noPermission());
                return;
            }
            give(sender, rest);
            return;
        }
        if (!plugin.hasAdminPermission(sender)) {
            sender.sendMessage(RrpPlugin.noPermission());
            return;
        }

        switch (sub) {
            case "gui", "menu" -> gui(sender);
            case "list" -> list(sender);
            case "catalog", "catalogue" -> catalog(sender, rest);
            case "info" -> info(sender, rest);
            case "install", "add" -> install(sender, rest);
            case "uninstall", "remove" -> uninstall(sender, rest);
            case "enable" -> setEnabled(sender, rest, true);
            case "disable" -> setEnabled(sender, rest, false);
            case "required" -> required(sender, rest);
            case "move" -> move(sender, rest);
            case "mode" -> mode(sender, rest);
            case "merge", "combine" -> merge(sender);
            case "apply", "send" -> apply(sender, rest);
            case "datapack", "dp" -> datapack(sender, rest);
            case "set" -> set(sender, rest);
            case "reload" -> reload(sender);
            case "help", "?" -> help(sender);
            default -> {
                sender.sendMessage(Msg.error("Unknown subcommand '<sub>'.", Msg.arg("sub", sub)));
                help(sender);
            }
        }
    }

    // --- subcommands -----------------------------------------------------------------------

    private void gui(CommandSender sender) {
        if (sender instanceof Player player) {
            menus.open(player, new MainMenu(service, menus));
        } else {
            sender.sendMessage(Msg.error("The GUI needs a player — try /rrp list."));
        }
    }

    private void list(CommandSender sender) {
        List<InstalledPack> packs = service.store().all();
        if (packs.isEmpty()) {
            sender.sendMessage(Msg.info("No packs installed. See /rrp catalog."));
            return;
        }
        sender.sendMessage(Msg.info("<count> pack(s), in application order:",
                Msg.num("count", packs.size())));
        int index = 1;
        for (InstalledPack pack : packs) {
            // The colour has to be part of the format string: a placeholder is replaced with
            // text, so it can never become a tag.
            String colour = pack.enabled() ? Msg.OK : Msg.MUTED;
            sender.sendMessage(Msg.raw("  <" + Msg.MUTED + "><n>. </" + Msg.MUTED + "><" + colour
                            + "><id></" + colour + "> <" + Msg.MUTED
                            + "><state> · <required> · <size> · datapack <datapack>",
                    Msg.num("n", index++),
                    Msg.arg("id", pack.id()),
                    Msg.arg("state", pack.enabled() ? "active" : "inactive"),
                    Msg.arg("required", pack.required() ? "required" : "optional"),
                    Msg.arg("size", Msg.bytes(pack.size())),
                    Msg.arg("datapack", pack.datapackInstalled() ? "yes" : "no")));
        }
        service.merged().ifPresentOrElse(
                merged -> sender.sendMessage(Msg.info("Combined into one pack (<size>): <url>",
                        Msg.arg("size", Msg.bytes(merged.size())),
                        Msg.arg("url", merged.url().isBlank() ? "no public URL set" : merged.url()))),
                () -> sender.sendMessage(Msg.info("Packs are sent stacked.")));
    }

    private void catalog(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("refresh")) {
            sender.sendMessage(Msg.info("Refreshing …"));
            service.refreshCatalog((success, message) -> sender.sendMessage(message));
            return;
        }
        List<CatalogPack> packs = service.catalog().packs();
        if (packs.isEmpty()) {
            sender.sendMessage(Msg.warn("The catalogue is empty — try /rrp catalog refresh."));
            return;
        }
        sender.sendMessage(Msg.info("<count> pack(s) in the catalogue:",
                Msg.num("count", packs.size())));
        for (CatalogPack pack : packs) {
            boolean installed = service.store().has(pack.id());
            sender.sendMessage(Msg.raw("  <" + Msg.ACCENT + "><id></" + Msg.ACCENT + "> <"
                            + Msg.MUTED + "><name> — <state>",
                    Msg.arg("id", pack.id()),
                    Msg.arg("name", pack.name()),
                    Msg.arg("state", installed ? "installed" : "not installed"))
                    .clickEvent(installed ? null : ClickEvent.suggestCommand("/rrp install " + pack.id())));
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Msg.error("Usage: /rrp info <pack>"));
            return;
        }
        String id = args[0];
        Optional<InstalledPack> installed = service.store().get(id);
        Optional<CatalogPack> entry = service.catalog().find(id);
        if (installed.isEmpty() && entry.isEmpty()) {
            sender.sendMessage(Msg.error("Nothing called '<id>' is installed or in the catalogue.",
                    Msg.arg("id", id)));
            return;
        }
        entry.ifPresent(pack -> {
            sender.sendMessage(Msg.info("<name>", Msg.arg("name", pack.name())));
            if (!pack.description().isBlank()) {
                sender.sendMessage(Msg.raw("  <" + Msg.MUTED + "><text>",
                        Msg.arg("text", pack.description())));
            }
            for (String module : pack.modules()) {
                sender.sendMessage(Msg.raw("  <" + Msg.MUTED + ">module </" + Msg.MUTED + "><"
                        + Msg.TEXT + "><module>", Msg.arg("module", module)));
            }
            for (CatalogItem item : pack.items()) {
                // Actually build it: that is the only honest way to say whether the item works
                // on this server right now, rather than whether it should.
                String state;
                String colour;
                try {
                    service.give().build(item, 1);
                    state = "ready";
                    colour = Msg.OK;
                } catch (GiveService.GiveException e) {
                    state = "unavailable — " + e.getMessage();
                    colour = Msg.WARN;
                }
                sender.sendMessage(Msg.raw("  <" + Msg.MUTED + ">item   </" + Msg.MUTED + "><"
                                + Msg.TEXT + "><key> <" + Msg.MUTED + "><name> <" + colour
                                + "><state>",
                        Msg.arg("key", item.key()), Msg.arg("name", item.name()),
                        Msg.arg("state", state)));
            }
        });
        installed.ifPresentOrElse(pack -> sender.sendMessage(Msg.info(
                        "installed · <state> · <required> · <size> · sha1 <sha1> · datapack <dp>",
                        Msg.arg("state", pack.enabled() ? "active" : "inactive"),
                        Msg.arg("required", pack.required() ? "required" : "optional"),
                        Msg.arg("size", Msg.bytes(pack.size())),
                        Msg.arg("sha1", pack.sha1()),
                        Msg.arg("dp", pack.datapackInstalled() ? "installed" : "not installed"))),
                () -> sender.sendMessage(Msg.info("Not installed — /rrp install <id>",
                        Msg.arg("id", id))));
    }

    private void install(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Msg.error("Usage: /rrp install <pack> — or /rrp install <url> <id>"));
            return;
        }
        String target = args[0];
        if (target.startsWith("http://") || target.startsWith("https://")) {
            if (args.length < 2) {
                sender.sendMessage(Msg.error("Installing from a URL needs an id: "
                        + "/rrp install <url> <id>"));
                return;
            }
            sender.sendMessage(Msg.info("Downloading …"));
            service.installFromUrl(target, args[1], (success, message) -> sender.sendMessage(message));
            return;
        }
        sender.sendMessage(Msg.info("Installing <id> …", Msg.arg("id", target)));
        service.install(target, (success, message) -> sender.sendMessage(message));
    }

    private void uninstall(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Msg.error("Usage: /rrp uninstall <pack>"));
            return;
        }
        service.uninstall(args[0], (success, message) -> sender.sendMessage(message));
    }

    private void setEnabled(CommandSender sender, String[] args, boolean enabled) {
        if (args.length == 0) {
            sender.sendMessage(Msg.error("Usage: /rrp <enable|disable> <pack>"));
            return;
        }
        service.setEnabled(args[0], enabled, (success, message) -> sender.sendMessage(message));
    }

    private void required(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.error("Usage: /rrp required <pack> <true|false>"));
            return;
        }
        service.setRequired(args[0], parseBoolean(args[1]),
                (success, message) -> sender.sendMessage(message));
    }

    private void move(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.error("Usage: /rrp move <pack> <up|down>"));
            return;
        }
        service.move(args[0], args[1].equalsIgnoreCase("up") ? -1 : 1,
                (success, message) -> sender.sendMessage(message));
    }

    private void mode(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Msg.info("Mode is <mode>. Use /rrp mode <auto|merged|stacked>.",
                    Msg.arg("mode", plugin.config().mode().name().toLowerCase(Locale.ROOT))));
            return;
        }
        RrpConfig.ApplyMode mode = RrpConfig.ApplyMode.parse(args[0], null);
        if (mode == null) {
            sender.sendMessage(Msg.error("Unknown mode '<mode>'. Use auto, merged or stacked.",
                    Msg.arg("mode", args[0])));
            return;
        }
        plugin.setConfigValue("apply.mode", mode.name().toLowerCase(Locale.ROOT));
        service.rebuildMerge((success, message) -> {
            sender.sendMessage(message);
            service.applyToAll();
        });
    }

    private void merge(CommandSender sender) {
        sender.sendMessage(Msg.info("Combining the active packs …"));
        service.rebuildMerge((success, message) -> {
            sender.sendMessage(message);
            service.merged().ifPresent(merged -> {
                if (!merged.conflicts().isEmpty()) {
                    sender.sendMessage(Msg.warn("<count> file(s) exist in more than one pack; "
                            + "the pack applied last wins.",
                            Msg.num("count", merged.conflicts().size())));
                }
            });
        });
    }

    private void apply(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("all")) {
            ApplyService.Result result = service.applyToAll();
            sender.sendMessage(Msg.success("Sent <count> pack(s) to everyone online (<how>).",
                    Msg.num("count", result.packsSent()),
                    Msg.arg("how", result.combined() ? "combined" : result.reason())));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Msg.error("'<name>' is not online.", Msg.arg("name", args[0])));
            return;
        }
        ApplyService.Result result = service.applyTo(target);
        sender.sendMessage(Msg.success("Sent <count> pack(s) to <name> (<how>).",
                Msg.num("count", result.packsSent()),
                Msg.arg("name", target.getName()),
                Msg.arg("how", result.combined() ? "combined" : result.reason())));
    }

    private void datapack(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.error("Usage: /rrp datapack <install|remove> <pack>"));
            return;
        }
        if (args[0].equalsIgnoreCase("install")) {
            service.installDatapack(args[1], (success, message) -> sender.sendMessage(message));
        } else {
            service.removeDatapack(args[1], (success, message) -> sender.sendMessage(message));
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Msg.error("Usage: /rrp give <item> [player|all] [amount]"));
            listItems(sender);
            return;
        }
        Optional<CatalogItem> item = service.catalog().findItem(args[0]);
        if (item.isEmpty()) {
            sender.sendMessage(Msg.error("No item called '<item>'.", Msg.arg("item", args[0])));
            listItems(sender);
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Msg.error("'<text>' is not a number.", Msg.arg("text", args[2])));
                return;
            }
        }

        List<Player> targets = new ArrayList<>();
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("all") || args[1].equals("@a")) {
                targets.addAll(plugin.getServer().getOnlinePlayers());
            } else {
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Msg.error("'<name>' is not online.", Msg.arg("name", args[1])));
                    return;
                }
                targets.add(target);
            }
        } else if (sender instanceof Player player) {
            targets.add(player);
        } else {
            sender.sendMessage(Msg.error("From the console, name a player: "
                    + "/rrp give <item> <player>"));
            return;
        }

        for (Player target : targets) {
            service.giveItem(item.get(), target, amount, (success, message) -> {
                sender.sendMessage(message);
                if (!target.equals(sender) && success) {
                    target.sendMessage(Msg.info("You received <item>.",
                            Msg.arg("item", item.get().name())));
                }
            });
        }
    }

    private void listItems(CommandSender sender) {
        List<CatalogItem> items = service.catalog().allItems();
        if (items.isEmpty()) {
            sender.sendMessage(Msg.warn("The catalogue lists no items — try /rrp catalog refresh."));
            return;
        }
        sender.sendMessage(Msg.info("Available items:"));
        for (CatalogItem item : items) {
            sender.sendMessage(Msg.raw("  <" + Msg.ACCENT + "><key></" + Msg.ACCENT + "> <"
                            + Msg.MUTED + "><name>",
                    Msg.arg("key", item.key()), Msg.arg("name", item.name()))
                    .clickEvent(ClickEvent.suggestCommand("/rrp give " + item.key())));
        }
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.error("Usage: /rrp set <key> <value> — keys: "
                    + String.join(", ", SETTABLE)));
            return;
        }
        String key = args[0].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (!SETTABLE.contains(key)) {
            sender.sendMessage(Msg.error("Unknown key '<key>'. Known keys: <keys>",
                    Msg.arg("key", key), Msg.arg("keys", String.join(", ", SETTABLE))));
            return;
        }
        Object parsed = switch (key) {
            case "apply.on-join", "apply.required-by-default", "datapacks.auto-install",
                 "datapacks.reload-after-install", "http.enabled", "security.require-https" ->
                    parseBoolean(value);
            case "http.port", "catalog.refresh-minutes", "security.max-download-mb" -> {
                try {
                    yield Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> value;
        };
        if (parsed == null) {
            sender.sendMessage(Msg.error("'<value>' is not valid for <key>.",
                    Msg.arg("value", value), Msg.arg("key", key)));
            return;
        }
        plugin.setConfigValue(key, parsed);
        if (key.startsWith("http.")) {
            plugin.restartHttpServer();
        }
        sender.sendMessage(Msg.success("<key> = <value>",
                Msg.arg("key", key), Msg.arg("value", String.valueOf(parsed))));
        if (key.startsWith("http.") || key.startsWith("apply.") || key.startsWith("merge.")) {
            service.rebuildMerge((success, message) -> sender.sendMessage(message));
        }
    }

    private void reload(CommandSender sender) {
        plugin.reloadRrpConfig();
        plugin.restartHttpServer();
        service.rebuildMerge((success, message) -> sender.sendMessage(message));
        sender.sendMessage(Msg.success("Configuration reloaded."));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Msg.info("Rain's Resourcepack Manager"));
        List<String> lines = List.of(
                "gui — the whole thing as a menu",
                "list — installed packs, in order",
                "catalog [refresh] — what the host offers",
                "info <pack> — details, items and modules",
                "install <pack> | install <url> <id>",
                "uninstall <pack>",
                "enable|disable <pack>",
                "required <pack> <true|false>",
                "move <pack> <up|down> — later packs win on conflicts",
                "mode <auto|merged|stacked> — how packs reach the client",
                "merge — rebuild the combined pack now",
                "apply [player|all] — send the packs again",
                "datapack <install|remove> <pack>",
                "give <item> [player|all] [amount]",
                "set <key> <value> — change config.yml",
                "reload — re-read config.yml");
        for (String line : lines) {
            sender.sendMessage(Msg.raw("  <" + Msg.ACCENT + ">/rrp </" + Msg.ACCENT + "><"
                    + Msg.TEXT + "><line>", Msg.arg("line", line)));
        }
    }

    private static final List<String> SETTABLE = List.of(
            "catalog.url", "catalog.refresh-minutes", "apply.on-join", "apply.mode",
            "apply.required-by-default", "apply.prompt", "merge.description", "http.enabled",
            "http.bind", "http.port", "http.public-url", "datapacks.auto-install",
            "datapacks.reload-after-install", "security.require-https", "security.max-download-mb");

    private static boolean parseBoolean(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.equals("true") || value.equals("yes") || value.equals("on")
                || value.equals("1");
    }

    // --- tab completion --------------------------------------------------------------------

    @Override
    public java.util.Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        boolean admin = plugin.hasAdminPermission(sender);
        boolean giver = plugin.hasGivePermission(sender);
        if (!admin && !giver) {
            return List.of();
        }

        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> subs = admin ? SUBCOMMANDS : List.of("give", "help");
            return subs.stream().filter(sub -> sub.startsWith(partial)).toList();
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);

        List<String> options = switch (sub) {
            case "give" -> args.length == 2
                    ? service.catalog().allItems().stream().map(CatalogItem::key).toList()
                    : args.length == 3 ? playerNames(true) : List.of("1", "8", "16", "32", "64");
            case "install", "add" -> args.length == 2
                    ? service.available().stream().map(CatalogPack::id).toList()
                    : List.of();
            case "uninstall", "remove", "info", "enable", "disable" -> args.length == 2
                    ? installedIds()
                    : List.of();
            case "required" -> args.length == 2 ? installedIds() : List.of("true", "false");
            case "move" -> args.length == 2 ? installedIds() : List.of("up", "down");
            case "mode" -> List.of("auto", "merged", "stacked");
            case "apply", "send" -> args.length == 2 ? playerNames(true) : List.of();
            case "datapack", "dp" -> args.length == 2
                    ? List.of("install", "remove")
                    : args.length == 3 ? installedIds() : List.of();
            case "catalog", "catalogue" -> List.of("refresh");
            case "set" -> args.length == 2 ? SETTABLE : suggestValueFor(args[1]);
            default -> List.of();
        };
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(partial))
                .sorted()
                .toList();
    }

    private List<String> suggestValueFor(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "apply.mode" -> List.of("auto", "merged", "stacked");
            case "apply.on-join", "apply.required-by-default", "datapacks.auto-install",
                 "datapacks.reload-after-install", "http.enabled", "security.require-https" ->
                    List.of("true", "false");
            default -> List.of();
        };
    }

    private List<String> installedIds() {
        return service.store().all().stream().map(InstalledPack::id).toList();
    }

    private List<String> playerNames(boolean withAll) {
        List<String> names = new ArrayList<>();
        if (withAll) {
            names.add("all");
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }
}
