package com.example.macromod.mixin;

import com.example.macromod.proxy.ProxyConfig;
import com.example.macromod.proxy.ProxyManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkingBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;

/**
 * Injects the appropriate proxy handler (SOCKS4/5, HTTP/HTTPS) into the Netty pipeline
 * when the proxy is enabled, before the connection is established.
 */
@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod-proxy");

    /**
     * Hook the static connect() that returns a ChannelFuture.
     * After the channel is created, add the proxy handler (based on protocol) as the
     * very first handler in the pipeline so all traffic routes through it.
     */
    @Inject(
        method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/network/NetworkingBackend;Lnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;",
        at = @At("RETURN")
    )
    private static void macromod$addProxyHandler(InetSocketAddress address,
                                                  NetworkingBackend backend,
                                                  ClientConnection connection,
                                                  CallbackInfoReturnable<ChannelFuture> cir) {
        try {
            if (!ProxyManager.getInstance().isEnabled()) return;

            ChannelFuture channelFuture = cir.getReturnValue();
            if (channelFuture == null) return;

            Channel channel = channelFuture.channel();
            if (channel == null) return;

            ProxyManager pm = ProxyManager.getInstance();
            ProxyConfig config = pm.getConfig();
            InetSocketAddress proxyAddr = pm.getProxyAddress();
            String protocol = config.getProtocol();

            // Create appropriate proxy handler based on protocol
            ChannelHandler proxyHandler = createProxyHandler(protocol, proxyAddr, config);
            if (proxyHandler == null) return;

            // Add as the very first handler in the pipeline
            String handlerName = "proxy-" + protocol.toLowerCase();
            channel.pipeline().addFirst(handlerName, proxyHandler);

            LOGGER.info("[Proxy] {} proxy injected: {}:{} -> {}:{}",
                protocol.toUpperCase(), proxyAddr.getHostString(), proxyAddr.getPort(),
                address.getHostString(), address.getPort());
        } catch (Exception e) {
            LOGGER.error("[Proxy] Failed to inject proxy handler", e);
            // Do NOT rethrow - let the connection continue without proxy
        }
    }

    /**
     * Creates the appropriate proxy handler based on protocol type.
     */
    private static ChannelHandler createProxyHandler(String protocol, InetSocketAddress proxyAddr,
                                                      ProxyConfig config) {
        if (protocol.equalsIgnoreCase(ProxyConfig.PROTO_HTTP) || 
            protocol.equalsIgnoreCase(ProxyConfig.PROTO_HTTPS)) {
            // HTTP/HTTPS proxy
            if (config.hasAuth()) {
                return new HttpProxyHandler(proxyAddr, config.getUsername(), config.getPassword());
            } else {
                return new HttpProxyHandler(proxyAddr);
            }
        } else if (protocol.equalsIgnoreCase(ProxyConfig.PROTO_SOCKS4)) {
            // SOCKS4: username only (no password in SOCKS4 protocol)
            io.netty.handler.proxy.ProxyHandler handler = config.hasAuth()
                ? new Socks4ProxyHandler(proxyAddr, config.getUsername())
                : new Socks4ProxyHandler(proxyAddr);
            handler.setConnectTimeoutMillis(10000);
            return handler;
        } else {
            // SOCKS5: username + password
            io.netty.handler.proxy.ProxyHandler handler = config.hasAuth()
                ? new Socks5ProxyHandler(proxyAddr, config.getUsername(), config.getPassword())
                : new Socks5ProxyHandler(proxyAddr);
            handler.setConnectTimeoutMillis(10000);
            return handler;
        }
    }
}
