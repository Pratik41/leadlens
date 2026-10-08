package com.leadlens.enrich;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * SSRF guard. Users type website addresses into this tool and the server fetches them, so without
 * this check "http://169.254.169.254/" (cloud metadata) or "http://localhost:8080/actuator" would
 * be fetched from inside our network. Only http(s) on default ports to public addresses is allowed,
 * and every redirect hop is checked again (SafeFetcher follows redirects itself for that reason).
 */
public final class AddressGuard {

    private AddressGuard() {
    }

    /** Null if the URI may be fetched, otherwise why not. */
    public static String rejectReason(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return "not a web address";
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return "only http and https are fetched";
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            return "non-standard port " + port;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
            || host.endsWith(".internal") || !host.contains(".")) {
            return "internal host name";
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return null;   // let the fetch fail as "unreachable": that's a dead site, not an attack
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                return "resolves to a private address";
            }
        }
        return null;
    }

    public static boolean isPublic(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
            || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first != 0                                       // 0.0.0.0/8
                && !(first == 100 && second >= 64 && second <= 127)  // 100.64.0.0/10 carrier-grade NAT
                && !(first == 192 && second == 0 && (b[2] & 0xff) == 0) // 192.0.0.0/24
                && !(first == 198 && (second == 18 || second == 19)) // 198.18.0.0/15 benchmarking
                && first < 224;                                      // multicast and reserved
        }
        if (a instanceof Inet6Address) {
            return (b[0] & 0xfe) != 0xfc;                            // fc00::/7 unique local
        }
        return false;
    }
}
