package de.chunkclaim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static de.chunkclaim.Messages.num;
import static de.chunkclaim.Messages.text;

/** Der Befehl /chunk mit allen Unterbefehlen. */
public final class ChunkCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS =
            List.of("claim", "unclaim", "info", "list", "set", "trust", "untrust", "help");

    private final ChunkClaimPlugin plugin;
    private final ClaimManager claims;
    private final Messages msg;

    public ChunkCommand(ChunkClaimPlugin plugin) {
        this.plugin = plugin;
        this.claims = plugin.claims();
        this.msg = plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "only-players");
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "claim" -> claim(player);
            case "unclaim" -> unclaim(player);
            case "info" -> info(player);
            case "list" -> list(player);
            case "set" -> set(player, args);
            case "trust" -> trust(player, args, true);
            case "untrust" -> trust(player, args, false);
            case "admin" -> admin(player, args);
            default -> help(player);
        }
        return true;
    }

    // --- /chunk claim ------------------------------------------------------

    private void claim(Player player) {
        if (!claims.isClaimableWorld(player.getWorld())) {
            msg.send(player, "claim.world");
            return;
        }
        ChunkKey key = ChunkKey.of(player.getLocation());
        Claim existing = claims.get(key);
        if (existing != null) {
            msg.send(player, existing.isOwner(player) ? "claim.already-yours" : "claim.already-claimed");
            msg.sound(player, "deny");
            return;
        }

        int owned = claims.countOf(player.getUniqueId());
        int max = claims.maxPerPlayer();
        if (owned >= max && !claims.hasBypass(player)) {
            msg.send(player, "claim.limit-reached", num("max", max));
            msg.sound(player, "deny");
            return;
        }

        int price = claims.priceForNext(owned);
        if (claims.hasBypass(player) && claims.freeForAdmins()) price = 0;

        Material currency = claims.currency();
        int have = claims.countItems(player, currency);
        if (have < price) {
            msg.send(player, "claim.not-enough", num("price", price), num("have", have));
            msg.sound(player, "deny");
            return;
        }

        claims.removeItems(player, currency, price);
        claims.create(key, player, price);

        TagResolver values = TagResolver.resolver(
                num("x", key.x()), num("z", key.z()), num("price", price),
                num("owned", owned + 1), num("max", max));
        msg.title(player, "claim.title", "claim.subtitle", values);
        msg.send(player, "claim.success", values);
        msg.sound(player, "claim");
    }

    // --- /chunk unclaim ----------------------------------------------------

    private void unclaim(Player player) {
        Claim claim = claims.get(player.getLocation());
        if (claim == null) {
            msg.send(player, "not-claimed");
            return;
        }
        if (!claim.isOwner(player)) {
            msg.send(player, claims.hasBypass(player) ? "unclaim.use-admin" : "not-owner");
            return;
        }
        int refund = claims.refundFor(claim);
        claims.remove(claim);
        claims.giveItems(player, claims.currency(), refund);
        msg.send(player, "unclaim.success",
                num("x", claim.key().x()), num("z", claim.key().z()), num("refund", refund));
        msg.sound(player, "unclaim");
    }

    // --- /chunk info -------------------------------------------------------

    private void info(Player player) {
        ChunkKey key = ChunkKey.of(player.getLocation());
        player.sendMessage(msg.get("info.header", num("x", key.x()), num("z", key.z())));

        Claim claim = claims.get(key);
        if (claim == null) {
            if (!claims.isClaimableWorld(player.getWorld())) {
                player.sendMessage(msg.get("info.not-claimable"));
                return;
            }
            int price = claims.priceForNext(claims.countOf(player.getUniqueId()));
            player.sendMessage(msg.get("info.free", num("price", price)));
            return;
        }

        player.sendMessage(msg.get("info.owner", text("owner", claim.ownerName())));
        for (Flag flag : Flag.values()) {
            player.sendMessage(msg.get("info.flag",
                    Placeholder.component("flag", msg.get("flag-names." + flag.id())),
                    Placeholder.component("state", msg.get(claim.flag(flag) ? "state.allowed" : "state.denied"))));
        }
        if (claim.trusted().isEmpty()) {
            player.sendMessage(msg.get("info.access-none"));
        } else {
            player.sendMessage(msg.get("info.access", text("players", String.join(", ", claim.trusted().values()))));
        }
    }

    // --- /chunk list -------------------------------------------------------

    private void list(Player player) {
        List<Claim> own = claims.claimsOf(player.getUniqueId());
        if (own.isEmpty()) {
            msg.send(player, "list.empty");
            return;
        }
        player.sendMessage(msg.get("list.header", num("count", own.size()), num("max", claims.maxPerPlayer())));
        for (Claim claim : own) {
            player.sendMessage(msg.get("list.entry",
                    num("x", claim.key().x()), num("z", claim.key().z()), text("world", claim.key().world())));
        }
    }

    // --- /chunk set [flag] [enable|disable] [all] --------------------------

    private void set(Player player, String[] args) {
        if (args.length < 3) {
            msg.send(player, "set.usage");
            return;
        }
        Flag flag = Flag.parse(args[1]);
        Boolean value = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "enable" -> Boolean.TRUE;
            case "disable" -> Boolean.FALSE;
            default -> null;
        };
        if (flag == null || value == null) {
            msg.send(player, "set.usage");
            return;
        }

        Claim claim = claims.get(player.getLocation());
        if (claim == null) {
            msg.send(player, "not-claimed");
            return;
        }
        if (!claims.canManage(player, claim)) {
            msg.send(player, "not-owner");
            return;
        }

        boolean all = args.length >= 4 && args[3].equalsIgnoreCase("all");
        List<Claim> targets = all ? claims.claimsOf(claim.owner()) : List.of(claim);
        for (Claim target : targets) target.setFlag(flag, value);
        claims.save();

        msg.send(player, all ? "set.success-all" : "set.success",
                Placeholder.component("flag", msg.get("flag-names." + flag.id())),
                Placeholder.component("state", msg.get(value ? "state.allowed" : "state.denied")),
                num("count", targets.size()));
    }

    // --- /chunk trust | untrust [Spieler] [all] ----------------------------

    private void trust(Player player, String[] args, boolean add) {
        if (args.length < 2) {
            msg.send(player, "trust.usage");
            return;
        }
        Claim claim = claims.get(player.getLocation());
        if (claim == null) {
            msg.send(player, "not-claimed");
            return;
        }
        if (!claims.canManage(player, claim)) {
            msg.send(player, "not-owner");
            return;
        }

        boolean all = args.length >= 3 && args[2].equalsIgnoreCase("all");
        List<Claim> targets = all ? claims.claimsOf(claim.owner()) : List.of(claim);
        String name = args[1];

        if (add) {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
            if (target == null) {
                msg.send(player, "trust.not-found", text("player", name));
                return;
            }
            if (target.getUniqueId().equals(claim.owner())) {
                msg.send(player, "trust.self");
                return;
            }
            String realName = target.getName() != null ? target.getName() : name;
            for (Claim t : targets) t.trust(target.getUniqueId(), realName);
            claims.save();
            msg.send(player, all ? "trust.added-all" : "trust.added",
                    text("player", realName), num("count", targets.size()));
        } else {
            UUID id = claim.findTrusted(name);
            if (id == null) {
                msg.send(player, "trust.not-trusted", text("player", name));
                return;
            }
            for (Claim t : targets) t.untrust(id);
            claims.save();
            msg.send(player, all ? "trust.removed-all" : "trust.removed",
                    text("player", name), num("count", targets.size()));
        }
    }

    // --- /chunk admin ... --------------------------------------------------

    private void admin(Player player, String[] args) {
        if (!player.hasPermission("chunkclaim.admin")) {
            msg.send(player, "no-permission");
            return;
        }
        if (args.length < 2) {
            msg.send(player, "admin.usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "unclaim" -> {
                Claim claim = claims.get(player.getLocation());
                if (claim == null) {
                    msg.send(player, "not-claimed");
                    return;
                }
                claims.remove(claim);
                msg.send(player, "admin.unclaim",
                        num("x", claim.key().x()), num("z", claim.key().z()), text("owner", claim.ownerName()));
            }
            case "bypass" -> {
                if (args.length < 3) {
                    msg.send(player, "admin.bypass-usage");
                    return;
                }
                switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "enable" -> {
                        claims.setBypass(player, true);
                        msg.send(player, "admin.bypass-on");
                    }
                    case "disable" -> {
                        claims.setBypass(player, false);
                        msg.send(player, "admin.bypass-off");
                    }
                    default -> msg.send(player, "admin.bypass-usage");
                }
            }
            case "reload" -> {
                plugin.reload();
                msg.send(player, "admin.reload");
            }
            default -> msg.send(player, "admin.usage");
        }
    }

    // --- /chunk help -------------------------------------------------------

    private void help(Player player) {
        player.sendMessage(msg.get("help.header"));
        for (Component line : msg.getList("help.lines")) player.sendMessage(line);
        if (player.hasPermission("chunkclaim.admin")) {
            for (Component line : msg.getList("help.admin-lines")) player.sendMessage(line);
        }
    }

    // --- Tab-Vervollständigung --------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        boolean admin = player.hasPermission("chunkclaim.admin");

        if (args.length == 1) {
            List<String> options = new ArrayList<>(SUBCOMMANDS);
            if (admin) options.add("admin");
            return filter(options, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("set")) {
            if (args.length == 2) return filter(List.of("break", "place", "interact"), args[1]);
            if (args.length == 3) return filter(List.of("enable", "disable"), args[2]);
            if (args.length == 4) return filter(List.of("all"), args[3]);
        } else if (sub.equals("trust")) {
            if (args.length == 2) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
            if (args.length == 3) return filter(List.of("all"), args[2]);
        } else if (sub.equals("untrust")) {
            if (args.length == 2) {
                Claim claim = claims.get(player.getLocation());
                List<String> names = claim == null ? List.of() : List.copyOf(claim.trusted().values());
                return filter(names, args[1]);
            }
            if (args.length == 3) return filter(List.of("all"), args[2]);
        } else if (sub.equals("admin") && admin) {
            if (args.length == 2) return filter(List.of("unclaim", "bypass", "reload"), args[1]);
            if (args.length == 3 && args[1].equalsIgnoreCase("bypass")) {
                return filter(List.of("enable", "disable"), args[2]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
