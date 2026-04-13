package com.example.macromod.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests proxy connectivity (SOCKS4, SOCKS5, HTTP, HTTPS) by connecting through the proxy.
 *
 * HTTP/HTTPS proxies: connect directly to the proxy and send a plain proxied GET request
 *   (GET http://api.ipify.org/ â€¦). This avoids the CONNECT-tunnel restriction that many
 *   HTTP proxies only allow for port 443.
 *
 * SOCKS4/SOCKS5 proxies: add Socks5ProxyHandler and connect to api.ipify.org:80 normally.
 */
public class ProxyTester {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod-proxy");
    private static final String TEST_HOST = "api.ipify.org";
    private static final int    TEST_PORT = 80;
    private static final int    TIMEOUT_MS = 8000;

    public static class TestResult {
        public final boolean success;
        public final long latencyMs;
        public final String detectedIp;
        public final String error;

        private TestResult(boolean success, long latencyMs, String detectedIp, String error) {
            this.success = success;
            this.latencyMs = latencyMs;
            this.detectedIp = detectedIp;
            this.error = error;
        }
        public static TestResult ok(long latencyMs, String ip) { return new TestResult(true, latencyMs, ip, null); }
        public static TestResult fail(String error)            { return new TestResult(false, -1, null, error); }
    }

    /** Tests asynchronously; the returned future completes on a Netty thread. */
    public static CompletableFuture<TestResult> testAsync(ProxyConfig config) {
        String proto = config.getProtocol();
        if (proto.equalsIgnoreCase(ProxyConfig.PROTO_HTTP) ||
            proto.equalsIgnoreCase(ProxyConfig.PROTO_HTTPS)) {
            return testHttpProxy(config);
        } else {
            return testSocksProxy(config);
        }
    }

    // â”€â”€ HTTP / HTTPS proxy â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Connects directly to the proxy and sends a full-URL GET (no CONNECT tunnel).
    // This is the standard plain-HTTP proxying method and works on all HTTP proxies.
    private static CompletableFuture<TestResult> testHttpProxy(ProxyConfig config) {
        CompletableFuture<TestResult> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();

        NioEventLoopGroup group = new NioEventLoopGroup(1);

        Bootstrap bootstrap = new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT_MS)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast("http-codec",      new HttpClientCodec());
                    ch.pipeline().addLast("http-aggregator", new HttpObjectAggregator(64 * 1024));
                    ch.pipeline().addLast("handler", new SimpleChannelInboundHandler<FullHttpResponse>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse resp) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            String body = resp.content().toString(StandardCharsets.UTF_8).trim();
                            LOGGER.info("[Proxy] HTTP proxy OK in {}ms â€” IP: {}", elapsed, body);
                            future.complete(TestResult.ok(elapsed, body));
                            ctx.close();
                        }
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            if (!future.isDone()) {
                                LOGGER.warn("[Proxy] HTTP proxy FAILED: {}", cause.getMessage());
                                future.complete(TestResult.fail(simplify(cause.getMessage())));
                            }
                            ctx.close();
                        }
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            // Full-URL request â€” this is the HTTP proxy protocol for plain HTTP
                            DefaultFullHttpRequest req = new DefaultFullHttpRequest(
                                HttpVersion.HTTP_1_1, HttpMethod.GET,
                                "http://" + TEST_HOST + "/");
                            req.headers().set(HttpHeaderNames.HOST, TEST_HOST);
                            req.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                            // Proxy-Authorization if credentials present
                            if (config.hasAuth()) {
                                String creds = config.getUsername() + ":" + config.getPassword();
                                String encoded = Base64.getEncoder().encodeToString(
                                    creds.getBytes(StandardCharsets.UTF_8));
                                req.headers().set("Proxy-Authorization", "Basic " + encoded);
                            }
                            ctx.writeAndFlush(req);
                        }
                    });
                }
            });

        // Connect directly to the proxy, not to the test host
        bootstrap.connect(config.getHost(), config.getPort()).addListener((ChannelFutureListener) cf -> {
            if (!cf.isSuccess()) {
                if (!future.isDone()) {
                    String msg = cf.cause() != null ? cf.cause().getMessage() : "Connection failed";
                    future.complete(TestResult.fail(simplify(msg)));
                }
            }
        });

        scheduleTimeout(group, future, TIMEOUT_MS);
        future.whenComplete((r, t) -> group.shutdownGracefully());
        return future;
    }

    // â”€â”€ SOCKS4 / SOCKS5 proxy â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Adds Socks5ProxyHandler which intercepts the connect and tunnels through the proxy.
    private static CompletableFuture<TestResult> testSocksProxy(ProxyConfig config) {
        CompletableFuture<TestResult> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();

        NioEventLoopGroup group = new NioEventLoopGroup(1);

        Bootstrap bootstrap = new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT_MS)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    InetSocketAddress proxyAddr = new InetSocketAddress(config.getHost(), config.getPort());
                    io.netty.handler.proxy.ProxyHandler socks;
                    if (config.getProtocol().equalsIgnoreCase(ProxyConfig.PROTO_SOCKS4)) {
                        // SOCKS4: supports only a username (no password field in SOCKS4)
                        socks = config.hasAuth()
                            ? new Socks4ProxyHandler(proxyAddr, config.getUsername())
                            : new Socks4ProxyHandler(proxyAddr);
                    } else {
                        // SOCKS5: supports username + password
                        socks = config.hasAuth()
                            ? new Socks5ProxyHandler(proxyAddr, config.getUsername(), config.getPassword())
                            : new Socks5ProxyHandler(proxyAddr);
                    }
                    socks.setConnectTimeoutMillis(TIMEOUT_MS);

                    ch.pipeline().addLast("socks",           socks);
                    ch.pipeline().addLast("http-codec",      new HttpClientCodec());
                    ch.pipeline().addLast("http-aggregator", new HttpObjectAggregator(64 * 1024));
                    ch.pipeline().addLast("handler", new SimpleChannelInboundHandler<FullHttpResponse>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse resp) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            String body = resp.content().toString(StandardCharsets.UTF_8).trim();
                            LOGGER.info("[Proxy] SOCKS proxy OK in {}ms â€” IP: {}", elapsed, body);
                            future.complete(TestResult.ok(elapsed, body));
                            ctx.close();
                        }
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            if (!future.isDone()) {
                                LOGGER.warn("[Proxy] SOCKS proxy FAILED: {}", cause.getMessage());
                                future.complete(TestResult.fail(simplify(cause.getMessage())));
                            }
                            ctx.close();
                        }
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            DefaultFullHttpRequest req = new DefaultFullHttpRequest(
                                HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
                            req.headers().set(HttpHeaderNames.HOST, TEST_HOST);
                            req.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                            ctx.writeAndFlush(req);
                        }
                    });
                }
            });

        // Connect to the test host â€” Socks5ProxyHandler intercepts and routes via proxy
        bootstrap.connect(TEST_HOST, TEST_PORT).addListener((ChannelFutureListener) cf -> {
            if (!cf.isSuccess()) {
                if (!future.isDone()) {
                    String msg = cf.cause() != null ? cf.cause().getMessage() : "Connection failed";
                    future.complete(TestResult.fail(simplify(msg)));
                }
            }
        });

        scheduleTimeout(group, future, TIMEOUT_MS);
        future.whenComplete((r, t) -> group.shutdownGracefully());
        return future;
    }

    private static void scheduleTimeout(NioEventLoopGroup group, CompletableFuture<TestResult> future, int ms) {
        group.schedule(() -> {
            if (!future.isDone()) {
                future.complete(TestResult.fail("Timeout (" + ms + "ms)"));
            }
        }, ms + 2000L, TimeUnit.MILLISECONDS);
    }

    /** Trim long Java exception messages to something readable in-game. */
    private static String simplify(String msg) {
        if (msg == null) return "Unknown error";
        int colon = msg.lastIndexOf(": ");
        return colon > 0 ? msg.substring(colon + 2) : msg;
    }
}
