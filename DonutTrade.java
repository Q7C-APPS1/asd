package dev.donuttrade;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DonutTrade extends JavaPlugin implements TabExecutor {

    private record Request(UUID from, long expires) {}

    private final Map<UUID, Request> requests = new HashMap<>();      // key = the player who received the request
    private final Map<UUID, TradeSession> sessions = new HashMap<>(); // key = each player in a trade

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new TradeListener(this), this);
        if (getCommand("trade") != null) {
            getCommand("trade").setExecutor(this);
            getCommand("trade").setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        for (TradeSession s : new ArrayList<>(sessions.values())) {
            s.abort("&cTrade cancelled: the server is stopping.");
        }
    }

    public void register(TradeSession s) {
        sessions.put(s.a.getUniqueId(), s);
        sessions.put(s.b.getUniqueId(), s);
    }

    public void unregister(TradeSession s) {
        sessions.remove(s.a.getUniqueId(), s);
        sessions.remove(s.b.getUniqueId(), s);
    }

    public TradeSession sessionOf(Player p) {
        return sessions.get(p.getUniqueId());
    }

    private void msg(Player p, String text) {
        p.sendMessage(color(text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can trade.");
            return true;
        }
        if (args.length == 0) {
            msg(p, "&eUsage: &f/trade <player> &7| &f/trade accept &7| &f/trade deny");
            return true;
        }
        String first = args[0].toLowerCase();
        if (first.equals("accept")) { handleAccept(p, args); return true; }
        if (first.equals("deny")) { handleDeny(p, args); return true; }
        handleRequest(p, args[0]);
        return true;
    }

    private void handleRequest(Player p, String targetName) {
        Player t = Bukkit.getPlayerExact(targetName);
        if (t == null || !t.isOnline()) { msg(p, "&cThat player is not online."); return; }
        if (t.getUniqueId().equals(p.getUniqueId())) { msg(p, "&cYou cannot trade with yourself."); return; }
        if (sessionOf(p) != null || sessionOf(t) != null) { msg(p, "&cYou or that player is already in a trade."); return; }
        long now = System.currentTimeMillis();
        Request mine = requests.get(p.getUniqueId());
        if (mine != null && mine.from().equals(t.getUniqueId()) && mine.expires() > now) {
            requests.remove(p.getUniqueId());
            new TradeSession(this, t, p);
            return;
        }
        long timeout = getConfig().getLong("request-timeout-seconds", 60L) * 1000L;
        requests.put(t.getUniqueId(), new Request(p.getUniqueId(), now + timeout));
        msg(p, "&aTrade request sent to &f" + t.getName() + "&a.");
        msg(t, "&e&l" + p.getName() + " &ewants to trade with you!");
        TextComponent accept = new TextComponent(color("&a&l[ACCEPT]"));
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept " + p.getName()));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to accept the trade")));
        TextComponent deny = new TextComponent(color("&c&l[DENY]"));
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny " + p.getName()));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to deny the trade")));
        TextComponent line = new TextComponent("   ");
        line.addExtra(accept);
        line.addExtra(new TextComponent("   "));
        line.addExtra(deny);
        t.spigot().sendMessage(line);
    }

    private Request pendingFor(Player p, String[] args) {
        Request r = requests.get(p.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) {
            requests.remove(p.getUniqueId());
            msg(p, "&cYou have no pending trade request.");
            return null;
        }
        if (args.length > 1) {
            Player named = Bukkit.getPlayerExact(args[1]);
            if (named == null || !named.getUniqueId().equals(r.from())) {
                msg(p, "&cYou have no request from that player.");
                return null;
            }
        }
        return r;
    }

    private void handleAccept(Player p, String[] args) {
        Request r = pendingFor(p, args);
        if (r == null) return;
        requests.remove(p.getUniqueId());
        Player from = Bukkit.getPlayer(r.from());
        if (from == null || !from.isOnline()) { msg(p, "&cThat player went offline."); return; }
        if (sessionOf(from) != null || sessionOf(p) != null) { msg(p, "&cSomeone is already in a trade."); return; }
        new TradeSession(this, from, p);
    }

    private void handleDeny(Player p, String[] args) {
        Request r = pendingFor(p, args);
        if (r == null) return;
        requests.remove(p.getUniqueId());
        msg(p, "&cTrade request denied.");
        Player from = Bukkit.getPlayer(r.from());
        if (from != null && from.isOnline()) msg(from, "&c" + p.getName() + " denied your trade request.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        List<String> out = new ArrayList<>(List.of("accept", "deny"));
        for (Player pl : Bukkit.getOnlinePlayers()) out.add(pl.getName());
        String typed = args[0].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(typed));
        return out;
    }
}
