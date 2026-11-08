package dev.donuttrade;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class TradeListener implements Listener {

    private final DonutTrade plugin;

    public TradeListener(DonutTrade plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof TradeSession.ViewHolder) {
            e.setCancelled(true);
            if (e.getRawSlot() == TradeSession.VIEW_BACK && e.getWhoClicked() instanceof Player viewer) {
                Bukkit.getScheduler().runTask(plugin, viewer::closeInventory);
            }
            return;
        }
        if (!(holder instanceof TradeSession.TradeHolder th)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        TradeSession s = th.session;
        if (s.ended || !p.getUniqueId().equals(th.owner)) {
            e.setCancelled(true);
            return;
        }
        int raw = e.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();
        ClickType ct = e.getClick();
        if (ct == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
            return;
        }
        if (inTop) {
            if (raw == TradeSession.ACCEPT) { e.setCancelled(true); s.toggleAccept(p); return; }
            if (raw == TradeSession.DENY) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> s.abort("&c" + p.getName() + " denied the trade."));
                return;
            }
            if (raw == TradeSession.SEE) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> s.openSee(p));
                return;
            }
            if (!TradeSession.isMine(raw)) {
                e.setCancelled(true);
                return;
            }
            if (ct.isShiftClick()) {
                e.setCancelled(true);
                ItemStack it = e.getCurrentItem();
                if (it != null) {
                    Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
                    top.setItem(raw, left.isEmpty() ? null : left.get(0));
                    s.scheduleChanged();
                }
                return;
            }
            s.scheduleChanged();
            return;
        }
        // click in the player's own inventory (bottom)
        if (ct.isShiftClick() && e.getClickedInventory() != null && e.getClickedInventory() != top) {
            e.setCancelled(true);
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType().isAir()) return;
            ItemStack rest = s.addToOffer(p.getUniqueId(), it.clone());
            e.setCurrentItem(rest);
            s.scheduleChanged();
        }
    }

    @EventHandler
    public void onCreative(InventoryCreativeEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof TradeSession.TradeHolder || h instanceof TradeSession.ViewHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof TradeSession.ViewHolder) {
            e.setCancelled(true);
            return;
        }
        if (!(holder instanceof TradeSession.TradeHolder th)) return;
        if (th.session.ended) {
            e.setCancelled(true);
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize() && !TradeSession.isMine(raw)) {
                e.setCancelled(true);
                return;
            }
        }
        th.session.scheduleChanged();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof TradeSession.ViewHolder vh) {
            TradeSession s = vh.session;
            if (e.getPlayer() instanceof Player p) {
                Bukkit.getScheduler().runTask(plugin, () -> s.reopen(p));
            }
            return;
        }
        if (holder instanceof TradeSession.TradeHolder th) {
            TradeSession s = th.session;
            if (s.ended || s.isSwitching(th.owner)) return;
            s.abort("&c" + e.getPlayer().getName() + " closed the trade.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        TradeSession s = plugin.sessionOf(e.getPlayer());
        if (s != null) s.abort("&c" + e.getPlayer().getName() + " left the game. Trade cancelled.");
    }
}
