package dev.hidearmor;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.ProtocolVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import io.github.retrooper.packetevents.impl.netty.manager.protocol.ProtocolManagerAbstract;

/** Real PacketEvents mappings/codecs, without starting a Minecraft server or mocking item components. */
final class PacketTestRuntime {
    static void initialize() {
        if (PacketEvents.getAPI() != null) return;
        PacketEvents.setAPI(new PacketEventsAPI<>() {
            private final NettyManager netty = new NettyManagerImpl();
            private final ProtocolManager protocol = new ProtocolManagerAbstract() {
                @Override
                public ProtocolVersion getPlatformVersion() { return ProtocolVersion.UNKNOWN; }
            };

            @Override public boolean isLoaded() { return true; }
            @Override public void init() {}
            @Override public boolean isInitialized() { return true; }
            @Override public boolean isTerminated() { return false; }
            @Override public Object getPlugin() { return this; }
            @Override public ServerManager getServerManager() { return () -> ServerVersion.V_26_3; }
            @Override public ProtocolManager getProtocolManager() { return protocol; }
            @Override public PlayerManager getPlayerManager() { return null; }
            @Override public NettyManager getNettyManager() { return netty; }
            @Override public ChannelInjector getInjector() { return null; }
        });
        PacketType.prepare();
    }
}
