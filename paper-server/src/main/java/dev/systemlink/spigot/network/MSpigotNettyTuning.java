package dev.systemlink.spigot.network;

import com.mojang.logging.LogUtils;
import dev.systemlink.spigot.configuration.MSpigotConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import java.lang.reflect.Field;
import org.slf4j.Logger;

/** Applies mSpigot networking options without forcing optional Netty/native classes to exist. */
public final class MSpigotNettyTuning {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MSpigotNettyTuning() {
    }

    public static ServerBootstrap applyServerOptions(final ServerBootstrap bootstrap) {
        final MSpigotConfig.Snapshot snapshot = MSpigotConfig.current();
        if (snapshot == null || !snapshot.networking().tcpFastOpen().enabled()) {
            return bootstrap;
        }
        final int queueSize = Math.max(1, snapshot.networking().tcpFastOpen().queueSize());
        final boolean configured = trySetServerOption(bootstrap, "io.netty.channel.epoll.EpollChannelOption", "TCP_FASTOPEN", queueSize)
            | trySetServerOption(bootstrap, "io.netty.channel.ChannelOption", "TCP_FASTOPEN", queueSize);
        if (!configured) {
            LOGGER.debug("mSpigot TCP_FASTOPEN requested, but this Netty/platform combination does not expose it");
        }
        return bootstrap;
    }

    public static void applyChildOptions(final Channel channel) {
        final MSpigotConfig.Snapshot snapshot = MSpigotConfig.current();
        if (snapshot == null) {
            return;
        }
        final MSpigotConfig.NetworkingSettings networking = snapshot.networking();
        try {
            channel.config().setOption(ChannelOption.TCP_NODELAY, networking.tcpNoDelay());
        } catch (final ChannelException ignored) {
        }
        final int ipTos = networking.ipTos();
        if (ipTos >= 0 && ipTos <= 255) {
            try {
                channel.config().setOption(ChannelOption.IP_TOS, ipTos);
            } catch (final ChannelException ignored) {
            }
        }
    }

    public static boolean flushConsolidationEnabled() {
        final MSpigotConfig.Snapshot snapshot = MSpigotConfig.current();
        return snapshot == null || snapshot.networking().flushConsolidation();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean trySetServerOption(final ServerBootstrap bootstrap, final String className, final String fieldName, final int value) {
        try {
            final Class<?> optionClass = Class.forName(className);
            final Field field = optionClass.getField(fieldName);
            final Object rawOption = field.get(null);
            if (rawOption instanceof ChannelOption channelOption) {
                bootstrap.option(channelOption, value);
                return true;
            }
        } catch (final ClassNotFoundException | NoSuchFieldException | IllegalAccessException | LinkageError ignored) {
        }
        return false;
    }
}
