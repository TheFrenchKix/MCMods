package com.example.macromod.proxy;

public class ProxyConfig {
    public static final String PROTO_SOCKS4  = "socks4";
    public static final String PROTO_SOCKS5  = "socks5";
    public static final String PROTO_HTTP    = "http";
    public static final String PROTO_HTTPS   = "https";

    private boolean enabled = false;
    private String protocol = PROTO_SOCKS5;
    private String host = "";
    private int port = 1080;
    private String username = "";
    private String password = "";
    private String name = "";     // optional display name for the proxy list

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol != null ? protocol : PROTO_SOCKS5; }

    public String getHost() { return host != null ? host : ""; }
    public void setHost(String host) { this.host = host != null ? host : ""; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username != null ? username : ""; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password != null ? password : ""; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name != null ? name : ""; }

    public boolean hasAuth() {
        return username != null && !username.isEmpty()
            && password != null && !password.isEmpty();
    }

    /**
     * Parses a proxy URL like: socks5://user:pass@host:port or http://host:port
     * Returns true if parsing succeeded, false if format is invalid.
     */
    public boolean parseFromUrl(String urlString) {
        if (urlString == null || urlString.isEmpty()) return false;

        try {
            String url = urlString.trim();

            // Extract protocol
            int protocolEnd = url.indexOf("://");
            if (protocolEnd == -1) return false;
            String proto = url.substring(0, protocolEnd).toLowerCase();

            // Normalize protocol
            if (!proto.equals(PROTO_SOCKS4) && !proto.equals(PROTO_SOCKS5)
                && !proto.equals(PROTO_HTTP) && !proto.equals(PROTO_HTTPS)) {
                return false;
            }
            this.protocol = proto;

            // Extract auth and host:port
            String remainder = url.substring(protocolEnd + 3);
            String auth = "";
            String hostPort = remainder;

            int atIndex = remainder.lastIndexOf('@');
            if (atIndex > 0) {
                auth = remainder.substring(0, atIndex);
                hostPort = remainder.substring(atIndex + 1);
            }

            // Parse auth if present
            if (!auth.isEmpty()) {
                int colonIndex = auth.indexOf(':');
                if (colonIndex > 0) {
                    this.username = auth.substring(0, colonIndex);
                    this.password = auth.substring(colonIndex + 1);
                } else {
                    this.username = auth;
                    this.password = "";
                }
            } else {
                this.username = "";
                this.password = "";
            }

            // Parse host:port
            int portIndex = hostPort.lastIndexOf(':');
            if (portIndex == -1 || portIndex == 0) return false;

            this.host = hostPort.substring(0, portIndex);
            String portStr = hostPort.substring(portIndex + 1);
            try {
                this.port = Integer.parseInt(portStr);
                if (this.port < 1 || this.port > 65535) return false;
            } catch (NumberFormatException e) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Converts config back to a URL string for display/export.
     */
    public String toUrl() {
        String u = username != null ? username : "";
        String p = password != null ? password : "";
        String result = protocol + "://";
        if (!u.isEmpty()) {
            result += u;
            if (!p.isEmpty()) result += ":" + p;
            result += "@";
        }
        result += host + ":" + port;
        return result;
    }
}
