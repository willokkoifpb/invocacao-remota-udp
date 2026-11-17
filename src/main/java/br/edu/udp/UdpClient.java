package br.edu.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class UdpClient {
    private static final AtomicInteger IDS = new AtomicInteger(new SecureRandom().nextInt());
    private final int timeoutMillis;
    private final int attempts;

    public UdpClient(int timeoutMillis, int attempts) {
        if (timeoutMillis < 1 || attempts < 1) {
            throw new IllegalArgumentException("Timeout e tentativas devem ser positivos.");
        }
        this.timeoutMillis = timeoutMillis;
        this.attempts = attempts;
    }

    public byte[] doOperation(RemoteRef remote, int operationId, byte[] arguments) throws IOException {
        int requestId = IDS.incrementAndGet();
        byte[] request = Protocol.encode(new Message(Message.REQUEST, requestId, remote, operationId, arguments));
        try (DatagramSocket socket = new DatagramSocket()) {
            // O socket conectado filtra datagramas de outros enderecos e portas.
            socket.connect(remote.address(), remote.port());
            for (int attempt = 0; attempt < attempts; attempt++) {
                socket.send(new DatagramPacket(request, request.length));
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
                while (System.nanoTime() < deadline) {
                    long remaining = deadline - System.nanoTime();
                    socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
                    byte[] buffer = new byte[Protocol.MAX_DATAGRAM + 1];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                    } catch (SocketTimeoutException e) {
                        break;
                    }
                    Message reply;
                    try {
                        reply = Protocol.decode(Arrays.copyOf(packet.getData(), packet.getLength()));
                    } catch (IOException e) {
                        continue;
                    }
                    if (reply.messageType() != Message.REPLY || reply.requestId() != requestId
                            || reply.operationId() != operationId || !reply.remoteReference().equals(remote)) {
                        continue;
                    }
                    byte[] body = reply.arguments();
                    if (body.length == 0 || (body[0] != 0 && body[0] != 1)) {
                        continue;
                    }
                    if (body[0] == 1) {
                        throw new RemoteOperationException(new String(body, 1, body.length - 1, StandardCharsets.UTF_8));
                    }
                    return Arrays.copyOfRange(body, 1, body.length);
                }
            }
        }
        throw new SocketTimeoutException("Sem resposta apos " + attempts + " tentativa(s) de " + timeoutMillis + " ms.");
    }
}
