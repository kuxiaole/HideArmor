package dev.hidearmor;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.PEVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HideArmorPlugin extends JavaPlugin implements Listener {
    static final String PERMISSION = "hidearmor.use";
    final ConcurrentHashMap<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private NamespacedKey preferencesKey;
    private ArmorPackets packets;
    private volatile boolean running;

    @Override
    public void onEnable() {
        var api = PacketEvents.getAPI();
        if (api == null || api.getVersion().compareTo(new PEVersion(2, 14, 0)) != 0) {
            getLogger().severe("HideArmor 需要 PacketEvents 2.14.0，请安装对应的 Spigot 版前置插件。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (api.getServerManager().getVersion() != ServerVersion.V_26_3) {
            getLogger().severe("此构建仅适用于 Minecraft / Folia 26.3，已停止启用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        preferencesKey = new NamespacedKey(this, "hidden_parts");
        packets = new ArmorPackets(this);
        running = true;
        api.getEventManager().registerListener(packets);
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("hidearmor")).setExecutor(this);
        Objects.requireNonNull(getCommand("hidearmor")).setTabCompleter((sender, command, alias, args) -> List.of());
        // Merely obtain scheduling handles here; do not read online players' live state on the global thread.
        for (var player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(this, task -> attach(player), null);
        }
        getLogger().info("HideArmor 已启用：/ha 打开菜单，权限 hidearmor.use（默认 OP）。");
    }

    @Override
    public void onDisable() {
        running = false;
        if (packets != null) PacketEvents.getAPI().getEventManager().unregisterListener(packets);
        HandlerList.unregisterAll((Listener) this);
        sessions.values().forEach(PlayerSession::dispose);
        sessions.clear();
        // Folia cancels outstanding owned tasks as well. Never read other regions' inventories during shutdown.
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        attach(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var state = sessions.get(event.getPlayer().getUniqueId());
        if (state != null && state.player == event.getPlayer()) detach(state);
    }

    private PlayerSession attach(Player player) {
        if (!running || !player.isOnline()) return null;
        var existing = sessions.get(player.getUniqueId());
        if (existing != null && !existing.closed.get() && existing.player == player) return existing;
        if (existing != null) detach(existing);
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            getLogger().warning("玩家 " + player.getName() + " 的 PacketEvents 会话尚未就绪。");
            return null;
        }
        int saved = player.getPersistentDataContainer().getOrDefault(preferencesKey, PersistentDataType.INTEGER, 0);
        var state = new PlayerSession(player, user, saved);
        sessions.put(state.id, state);
        state.permissionTask = state.scheduler.runAtFixedRate(this, task -> poll(state), () -> detach(state), 1, 20);
        if (state.permissionTask == null) {
            detach(state);
            return null;
        }
        requestRefresh(state);
        return state;
    }

    private void detach(PlayerSession state) {
        sessions.remove(state.id, state);
        state.dispose();
    }

    private void poll(PlayerSession state) {
        if (!running || state.closed.get() || !state.player.isOnline()) {
            detach(state);
            return;
        }
        var player = state.player;
        boolean permitted = player.hasPermission(PERMISSION);
        int effective = permitted ? state.selectedMask : 0;
        boolean revived = state.wasDead && !player.isDead();
        state.wasDead = player.isDead();
        if (!permitted && player.getOpenInventory().getTopInventory().getHolder(false) instanceof ArmorMenu) {
            player.closeInventory();
        }
        // Reapply the saved choice if the respawn path replaced the entity's persistent data.
        save(state);
        if (state.effectiveMask != effective || revived) {
            state.effectiveMask = effective;
            requestRefresh(state);
        }
    }

    private void save(PlayerSession state) {
        var data = state.player.getPersistentDataContainer();
        if (data.getOrDefault(preferencesKey, PersistentDataType.INTEGER, 0) != state.selectedMask) {
            data.set(preferencesKey, PersistentDataType.INTEGER, state.selectedMask);
        }
    }

    void requestRefresh(PlayerSession state) {
        if (!running || state.closed.get() || !state.refreshQueued.compareAndSet(false, true)) return;
        state.refreshTask = state.scheduler.run(this, task -> {
            state.refreshQueued.set(false);
            state.refreshTask = null;
            if (running && !state.closed.get() && state.player.isOnline()) refresh(state);
        }, () -> detach(state));
        if (state.refreshTask == null) state.refreshQueued.set(false);
    }

    private void refresh(PlayerSession state) {
        var player = state.player;
        if (player.isDead()) return;
        state.effectiveMask = player.hasPermission(PERMISSION) ? state.selectedMask : 0;
        if (state.closeMenu) {
            state.closeMenu = false;
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof ArmorMenu) {
                player.closeInventory();
            }
        }
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof ArmorMenu menu) {
            menu.render(state.selectedMask);
        }
        player.updateInventory();
        var inventory = player.getInventory();
        // The open chest menu does not include the armor slots; refresh the underlying player inventory too.
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            state.user.sendPacket(new WrapperPlayServerSetPlayerInventory(slot, convert(inventory.getItem(slot))));
        }
        var equipment = List.of(
                new Equipment(EquipmentSlot.HELMET, convert(inventory.getHelmet())),
                new Equipment(EquipmentSlot.CHEST_PLATE, convert(inventory.getChestplate())),
                new Equipment(EquipmentSlot.LEGGINGS, convert(inventory.getLeggings())),
                new Equipment(EquipmentSlot.BOOTS, convert(inventory.getBoots())));
        // ponytail: scan online viewers only on refresh; use a reverse tracking index if very large servers need it.
        for (var viewer : sessions.values()) {
            if (viewer == state || viewer.closed.get()) continue;
            viewer.trackedPlayers.forEach((entityId, uuid) -> {
                if (uuid.equals(state.id)) {
                    viewer.user.sendPacket(new WrapperPlayServerEntityEquipment(entityId, equipment));
                }
            });
        }
    }

    private static ItemStack convert(org.bukkit.inventory.ItemStack item) {
        return item == null || item.isEmpty() ? ItemStack.EMPTY : SpigotConversionUtil.fromBukkitItemStack(item);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以打开盔甲隐藏菜单。");
            return true;
        }
        player.getScheduler().run(this, task -> {
            if (!running || !player.isOnline()) return;
            if (!player.hasPermission(PERMISSION)) {
                denied(player);
                return;
            }
            var state = attach(player);
            if (state == null) {
                player.sendMessage(Component.text("暂时无法打开菜单，请稍后重试。", NamedTextColor.RED));
                return;
            }
            if (args.length != 0) {
                player.sendMessage(Component.text("用法：/hidearmor 或 /ha", NamedTextColor.YELLOW));
                return;
            }
            player.openInventory(new ArmorMenu(state.id, state.selectedMask).getInventory());
        }, null);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ArmorMenu menu)) return;
        event.setCancelled(true); // Includes the player's bottom inventory, hotbar keys, shift-click and drops.
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var state = sessions.get(player.getUniqueId());
        if (state == null || !menu.owner.equals(state.id)) return;
        if (!player.hasPermission(PERMISSION)) {
            state.effectiveMask = 0;
            state.closeMenu = true;
            denied(player);
            requestRefresh(state);
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        if (slot == ArmorMenu.CLOSE) {
            state.closeMenu = true;
        } else if (slot == ArmorMenu.HIDE_ALL) {
            state.selectedMask = HidePart.ALL;
        } else if (slot == ArmorMenu.SHOW_ALL) {
            state.selectedMask = 0;
        } else {
            HidePart clicked = null;
            for (var part : HidePart.values()) if (part.menuSlot == slot) clicked = part;
            if (clicked == null) return;
            state.selectedMask ^= clicked.bit();
        }
        state.effectiveMask = state.selectedMask;
        save(state);
        requestRefresh(state); // Inventory opens/closes and packet refreshes occur after the click event.
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ArmorMenu) event.setCancelled(true);
    }

    private static void denied(Player player) {
        player.sendMessage(Component.text("你没有使用盔甲隐藏菜单的权限。", NamedTextColor.RED));
    }
}
