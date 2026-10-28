package dev.hidearmor;

import com.github.retrooper.packetevents.protocol.player.User;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class PlayerSession {
    final UUID id;
    final Player player; // Access only inside its entity context; released on quit/retirement/disable.
    final User user;
    final EntityScheduler scheduler;
    final DisplayItems display = new DisplayItems();
    final ConcurrentHashMap<Integer, UUID> trackedPlayers = new ConcurrentHashMap<>();
    final AtomicBoolean refreshQueued = new AtomicBoolean();
    final AtomicBoolean closed = new AtomicBoolean();
    volatile int effectiveMask;
    volatile ScheduledTask permissionTask;
    volatile ScheduledTask refreshTask;
    // The following fields belong exclusively to the player's entity context.
    int selectedMask;
    boolean wasDead;
    boolean closeMenu;

    PlayerSession(Player player, User user, int selectedMask) {
        this.id = player.getUniqueId();
        this.player = player;
        this.user = user;
        this.scheduler = player.getScheduler();
        this.selectedMask = selectedMask & HidePart.ALL;
        this.effectiveMask = player.hasPermission(HideArmorPlugin.PERMISSION) ? this.selectedMask : 0;
        this.wasDead = player.isDead();
    }

    void dispose() {
        if (!closed.compareAndSet(false, true)) return;
        effectiveMask = 0;
        var permission = permissionTask;
        if (permission != null) permission.cancel();
        var refresh = refreshTask;
        if (refresh != null) refresh.cancel();
        refreshQueued.set(false);
        trackedPlayers.clear();
        display.clear();
    }
}
