/**
 * Validates the Ollama base URL so the plugin cannot be pointed at non-HTTP schemes,
 * credential-bearing URLs, or well-known cloud metadata endpoints.
 * LAN and localhost remain allowed. Ported from EaGPT (fideocam/sparxgpt).
 */
package com.archimatetool.archigpt;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;

@SuppressWarnings("nls")
public final class OllamaEndpoint {

    public static final int MAX_URL_LENGTH = 2048;

    private OllamaEndpoint() {}

    /**
     * Turns a user-entered host or URL into an Ollama API origin.
     * Blank or unsafe input becomes {@link OllamaClient#DEFAULT_BASE_URL}.
     */
    public static String normalizeOrDefault(String raw) {
        NormalizeResult r = tryNormalize(raw);
        return r.ok ? r.normalized : OllamaClient.DEFAULT_BASE_URL;
    }

    public static NormalizeResult tryNormalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return NormalizeResult.fail("Ollama URL is empty.");
        }
        String s = raw.trim();
        if (s.length() > MAX_URL_LENGTH) {
            return NormalizeResult.fail("Ollama URL is too long.");
        }
        if (!s.contains("://")) {
            s = "http://" + s;
        }
        URL uri;
        try {
            uri = new URL(s);
        } catch (MalformedURLException e) {
            return NormalizeResult.fail("Ollama URL is not a valid absolute URI.");
        }
        String protocol = uri.getProtocol();
        if (protocol == null
                || (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol))) {
            return NormalizeResult.fail("Ollama URL must be http or https.");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
            return NormalizeResult.fail("Ollama URL must not contain credentials.");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return NormalizeResult.fail("Ollama URL must include a host.");
        }
        if (isBlockedHost(host)) {
            return NormalizeResult.fail("Ollama URL host is not allowed.");
        }
        int port = uri.getPort();
        if (port == -1 && "http".equalsIgnoreCase(protocol)) {
            port = OllamaClient.DEFAULT_PORT;
        }
        StringBuilder out = new StringBuilder();
        out.append(protocol.toLowerCase(Locale.ROOT)).append("://");
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            out.append('[').append(host).append(']');
        } else {
            out.append(host);
        }
        if (port != -1) {
            out.append(':').append(port);
        }
        // Keep a non-root path so a reverse proxy in front of Ollama still works.
        // Strip Ollama API suffixes if the user pasted a full /api/chat URL.
        String path = uri.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            String stripped = stripOllamaApiSuffix(path);
            if (stripped.endsWith("/")) {
                stripped = stripped.substring(0, stripped.length() - 1);
            }
            if (!stripped.isEmpty() && !"/".equals(stripped)) {
                out.append(stripped);
            }
        }
        return NormalizeResult.ok(out.toString());
    }

    static String stripOllamaApiSuffix(String path) {
        String p = path;
        String[] apis = { "/api/chat", "/api/tags", "/api/show", "/api/generate", "/api/embed" };
        for (int i = 0; i < apis.length; i++) {
            if (p.endsWith(apis[i])) {
                return p.substring(0, p.length() - apis[i].length());
            }
        }
        return p;
    }

    public static boolean isBlockedHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            return true;
        }
        String h = stripHost(host);
        if (h.equalsIgnoreCase("169.254.169.254")
                || h.equalsIgnoreCase("metadata.google.internal")
                || h.equalsIgnoreCase("metadata")
                || h.equalsIgnoreCase("instance-data")
                || h.equalsIgnoreCase("100.100.100.200")) {
            return true;
        }
        InetAddress ip = tryParseHostAsIp(h);
        return ip != null && isBlockedAddress(ip);
    }

    static String stripHost(String host) {
        String h = host.trim();
        int zone = h.indexOf('%');
        if (zone >= 0) {
            h = h.substring(0, zone);
        }
        if (h.startsWith("[") && h.endsWith("]") && h.length() >= 2) {
            h = h.substring(1, h.length() - 1);
        }
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        return h;
    }

    static InetAddress tryParseHostAsIp(String host) {
        String h = stripHost(host);
        if (h.isEmpty() || !looksLikeIpLiteral(h)) {
            return null;
        }
        if (h.indexOf(':') >= 0) {
            try {
                // IPv6 / IPv4-mapped literals only — getByName does not DNS these.
                return InetAddress.getByName(h);
            } catch (UnknownHostException e) {
                return null;
            }
        }
        InetAddress dword = tryParseDwordIpv4(h);
        if (dword != null && h.indexOf('.') < 0) {
            return dword;
        }
        String[] parts = h.split("\\.");
        if (parts.length != 4) {
            return dword;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            int n = tryParseIpv4Octet(parts[i]);
            if (n < 0) {
                return null;
            }
            bytes[i] = (byte) n;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** True for numeric IPv4/IPv6 literals, including hex/octal/dword encodings. No hostnames. */
    static boolean looksLikeIpLiteral(String h) {
        if (h.indexOf(':') >= 0) {
            for (int i = 0; i < h.length(); i++) {
                char c = h.charAt(i);
                if (!(c == ':' || c == '.' || c == '%' || (c >= '0' && c <= '9')
                        || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                    return false;
                }
            }
            return true;
        }
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (!(c == '.' || c == 'x' || c == 'X'
                    || (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    static boolean isBlockedAddress(InetAddress ip) {
        if (ip == null) {
            return false;
        }
        byte[] b = ip.getAddress();
        if (b.length == 16 && isIpv4Mapped(b)) {
            b = new byte[] { b[12], b[13], b[14], b[15] };
        }
        if (b.length == 4) {
            int a0 = b[0] & 0xFF;
            int a1 = b[1] & 0xFF;
            int a2 = b[2] & 0xFF;
            int a3 = b[3] & 0xFF;
            if (a0 == 169 && a1 == 254) {
                return true;
            }
            if (a0 == 100 && a1 == 100 && a2 == 100 && a3 == 200) {
                return true;
            }
        }
        if (b.length == 16) {
            try {
                InetAddress aws = InetAddress.getByName("fd00:ec2::254");
                if (ip.equals(aws)) {
                    return true;
                }
            } catch (UnknownHostException ignored) {
            }
        }
        return false;
    }

    private static boolean isIpv4Mapped(byte[] b) {
        if (b.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return b[10] == (byte) 0xFF && b[11] == (byte) 0xFF;
    }

    private static InetAddress tryParseDwordIpv4(String host) {
        long dword;
        try {
            if (host.length() > 2 && host.regionMatches(true, 0, "0x", 0, 2)) {
                dword = Long.parseLong(host.substring(2), 16);
            } else {
                dword = Long.parseLong(host);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (dword < 0 || dword > 0xFFFFFFFFL) {
            return null;
        }
        byte[] bytes = new byte[] {
                (byte) ((dword >> 24) & 0xFF),
                (byte) ((dword >> 16) & 0xFF),
                (byte) ((dword >> 8) & 0xFF),
                (byte) (dword & 0xFF)
        };
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static int tryParseIpv4Octet(String raw) {
        if (raw == null || raw.isEmpty()) {
            return -1;
        }
        try {
            int n;
            if (raw.length() > 2 && raw.regionMatches(true, 0, "0x", 0, 2)) {
                n = Integer.parseInt(raw.substring(2), 16);
            } else if (raw.length() > 1 && raw.startsWith("0")) {
                n = Integer.parseInt(raw, 8);
            } else {
                n = Integer.parseInt(raw);
            }
            if (n < 0 || n > 255) {
                return -1;
            }
            return n;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static final class NormalizeResult {
        public final boolean ok;
        public final String normalized;
        public final String error;

        private NormalizeResult(boolean ok, String normalized, String error) {
            this.ok = ok;
            this.normalized = normalized;
            this.error = error;
        }

        static NormalizeResult ok(String normalized) {
            return new NormalizeResult(true, normalized, "");
        }

        static NormalizeResult fail(String error) {
            return new NormalizeResult(false, OllamaClient.DEFAULT_BASE_URL, error);
        }
    }
}
