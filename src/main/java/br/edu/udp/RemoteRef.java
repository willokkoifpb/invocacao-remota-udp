package br.edu.udp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public record RemoteRef(String host, int port) {
    public RemoteRef {
        if (host == null || host.isBlank() || host.getBytes(StandardCharsets.UTF_8).length > 255) {
            throw new IllegalArgumentException("Host deve ter entre 1 e 255 bytes UTF-8.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Porta deve estar entre 1 e 65535.");
        }
    }

    public InetAddress address() throws UnknownHostException {
        return InetAddress.getByName(host);
    }
}
