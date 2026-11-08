package dev.donuttrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One trade between two players. Each player gets a 54-slot window:
 * left 4x4 = your offer (editable), right 4x4 = their offer (read only),
 * bottom right = ACCEPT / DENY / SEE THEIR ITEMS.
 */
public final class TradeSession {

    public static final int STATUS_ME = 45, INFO = 48, VIEW_BACK = 49, STATUS_THEM = 50, ACCEPT = 51, DENY = 52, SEE = 53;

    public static boolean isMine(int slot) { return slot >= 0 && slot < 54 && slot / 9 < 4 && slot % 9 < 4; }
    public static boolean isTheirs(int slot) { return slot >= 0 && slot < 54 && slot / 9 < 4 && slot % 9 >= 5; }

    public static final class TradeHolder implements InventoryHolder {
        public final TradeSession session;
        public final UUID owner;
        TradeHolder(TradeSession session, UUID owner) { this.session = session; this.owner = owner; }
        @Override public Inventory getInventory() { return session.invOf(owner); }
    }

    public static final class ViewHolder implements InventoryHolder {
        public final TradeSession session;
        public final UUID viewer;
        Inventory inv;
        ViewHolder(TradeSession session, UUID viewer) { this.session = session; this.viewer = viewer; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final DonutTrade plugin;
    public final Player a, b;
    private final Inventory ia, ib;
    private boolean accA, accB;
    public boolean ended;
    private BukkitTask countdown;
    private int remaining;
    private final Set<UUID> switching = new HashSet<>();

    public TradeSession(DonutTrade plugin, Player a, Player b) {
        this.plugin = plugin;
        this.a = a;
        this.b = b;
        ia = Bukkit.createInventory(new TradeHolder(this, a.getUniqueId()), 54, DonutTrade.color("&8Trade with &0" + b.getName()));
        ib = Bukkit.createInventory(new TradeHolder(this, b.getUniqueId()), 54, DonutTrade.color("&8Trade with &0" + a.getName()));
        buildFrame(ia);
        buildFrame(ib);
        plugin.register(this);
        refresh();
        a.openInventory(ia);
        b.openInventory(ib);
    }

    static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DonutTrade.color(name));
            if (lore != null) {
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(DonutTrade.color(s));
                meta.setLore(l);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    public Inventory invOf(UUID id) { return a.getUniqueId().equals(id) ? ia : ib; }
    private Player playerOf(UUID id) { return a.getUniqueId().equals(id) ? a : b; }
    private Player otherOf(UUID id) { return a.getUniqueId().equals(id) ? b : a; }
    public boolean isSwitching(UUID id) { return switching.contains(id); }

    private void buildFrame(Inventory inv) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            if (!isMine(i) && !isTheirs(i)) inv.setItem(i, pane);
        }
    }

    /** copy both offers to the other side's read-only area and repaint buttons */
    public void refresh() {
        mirror(ia, ib);
        mirror(ib, ia);
        paintBottom(ia, b, accA, accB);
        paintBottom(ib, a, accB, accA);
    }

    private void mirror(Inventory src, Inventory dst) {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                ItemStack it = src.getItem(r * 9 + c);
                dst.setItem(r * 9 + c + 5, it == null ? null : it.clone());
            }
        }
    }

    private ItemStack status(boolean accepted, String countdownText, String who) {
        if (countdownText != null) return item(Material.YELLOW_STAINED_GLASS_PANE, countdownText, null);
        if (accepted) return item(Material.LIME_STAINED_GLASS_PANE, "&a" + who + " accepted", null);
        return item(Material.RED_STAINED_GLASS_PANE, "&c" + who + " has not accepted", null);
    }

    private void paintBottom(Inventory inv, Player other, boolean meAcc, boolean otherAcc) {
        String cd = countdown != null ? "&eTrade completes in " + remaining + "s..." : null;
        inv.setItem(STATUS_ME, status(meAcc, cd, "You"));
        inv.setItem(STATUS_THEM, status(otherAcc, cd, other.getName()));
        inv.setItem(INFO, item(Material.NAME_TAG, "&e&lTrading with &f" + other.getName(),
                List.of("&7Left side: &fyour items", "&7Right side: &ftheir items")));
        inv.setItem(ACCEPT, meAcc
                ? item(Material.GREEN_CONCRETE, "&a&lACCEPTED", List.of("&7Click to take back your accept"))
                : item(Material.LIME_CONCRETE, "&a&lACCEPT", List.of("&7Click to accept the trade")));
        inv.setItem(DENY, item(Material.RED_CONCRETE, "&c&lDENY", List.of("&7Cancel the trade")));
        inv.setItem(SEE, item(Material.ENDER_EYE, "&b&lSEE &f" + other.getName() + "&b's ITEMS",
                List.of("&7Open a big view of their offer")));
    }

    private boolean offerEmpty(Inventory inv) {
        for (int i = 0; i < 54; i++) if (isMine(i) && inv.getItem(i) != null) return false;
        return true;
    }

    public void scheduleChanged() {
        Bukkit.getScheduler().runTask(plugin, this::onOfferChanged);
    }

    /** an offer changed: both accepts are reset */
    public void onOfferChanged() {
        if (ended) return;
        accA = false;
        accB = false;
        stopCountdown();
        refresh();
    }

    public void toggleAccept(Player p) {
        if (ended) return;
        boolean isA = p.getUniqueId().equals(a.getUniqueId());
        boolean current = isA ? accA : accB;
        if (!current && offerEmpty(ia) && offerEmpty(ib)) {
            p.sendMessage(DonutTrade.color("&eAdd some items first."));
            return;
        }
        if (isA) accA = !accA; else accB = !accB;
        if (accA && accB) startCountdown(); else stopCountdown();
        refresh();
    }

    private void startCountdown() {
        stopCountdown();
        remaining = Math.max(1, plugin.getConfig().getInt("countdown-seconds", 3));
        countdown = new BukkitRunnable() {
            @Override
            public void run() {
                remaining--;
                if (remaining <= 0) {
                    cancel();
                    countdown = null;
                    complete();
                } else {
                    refresh();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
    }

    private List<ItemStack> take(Inventory inv) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            if (!isMine(i)) continue;
            ItemStack it = inv.getItem(i);
            if (it != null) {
                out.add(it);
                inv.setItem(i, null);
            }
        }
        return out;
    }

    private void give(Player p, List<ItemStack> items) {
        if (items.isEmpty()) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(items.toArray(new ItemStack[0]));
        for (ItemStack it : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), it);
    }

    private void complete() {
        if (ended) return;
        if (!a.isOnline() || !b.isOnline()) {
            abort("&cTrade cancelled: a player left.");
            return;
        }
        ended = true;
        stopCountdown();
        List<ItemStack> fromA = take(ia);
        List<ItemStack> fromB = take(ib);
        give(b, fromA);
        give(a, fromB);
        plugin.unregister(this);
        closeLater();
        a.sendMessage(DonutTrade.color("&a&lTrade complete!"));
        b.sendMessage(DonutTrade.color("&a&lTrade complete!"));
    }

    /** close both windows one tick later (closing inside an inventory event can glitch) */
    private void closeLater() {
        Runnable r = () -> {
            if (a.isOnline()) a.closeInventory();
            if (b.isOnline()) b.closeInventory();
        };
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, r); else r.run();
    }

    /** cancel the trade and give everyone their own items back */
    public void abort(String reason) {
        if (ended) return;
        ended = true;
        stopCountdown();
        give(a, take(ia));
        give(b, take(ib));
        plugin.unregister(this);
        closeLater();
        if (a.isOnline()) a.sendMessage(DonutTrade.color(reason));
        if (b.isOnline()) b.sendMessage(DonutTrade.color(reason));
    }

    /** big read-only view of the other player's offer */
    public void openSee(Player p) {
        if (ended) return;
        Player other = otherOf(p.getUniqueId());
        ViewHolder vh = new ViewHolder(this, p.getUniqueId());
        Inventory v = Bukkit.createInventory(vh, 54, DonutTrade.color("&8" + other.getName() + "&8's items"));
        vh.inv = v;
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) v.setItem(i, pane);
        Inventory src = invOf(other.getUniqueId());
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                ItemStack it = src.getItem(r * 9 + c);
                v.setItem(r * 9 + c + 2, it == null ? null : it.clone());
            }
        }
        v.setItem(VIEW_BACK, item(Material.BARRIER, "&c&lBACK", List.of("&7Return to the trade")));
        switching.add(p.getUniqueId());
        try {
            p.openInventory(v);
        } finally {
            switching.remove(p.getUniqueId());
        }
    }

    public void reopen(Player p) {
        if (ended || !p.isOnline()) return;
        switching.add(p.getUniqueId());
        try {
            p.openInventory(invOf(p.getUniqueId()));
        } finally {
            switching.remove(p.getUniqueId());
        }
    }

    /** put an item stack into the player's offer area; returns what did not fit (or null) */
    public ItemStack addToOffer(UUID id, ItemStack it) {
        Inventory inv = invOf(id);
        for (int i = 0; i < 54; i++) {
            if (!isMine(i)) continue;
            ItemStack cur = inv.getItem(i);
            if (cur != null && cur.isSimilar(it) && cur.getAmount() < cur.getMaxStackSize()) {
                int add = Math.min(it.getAmount(), cur.getMaxStackSize() - cur.getAmount());
                cur.setAmount(cur.getAmount() + add);
                it.setAmount(it.getAmount() - add);
                inv.setItem(i, cur);
                if (it.getAmount() <= 0) return null;
            }
        }
        for (int i = 0; i < 54; i++) {
            if (isMine(i) && inv.getItem(i) == null) {
                inv.setItem(i, it);
                return null;
            }
        }
        return it;
    }
}
