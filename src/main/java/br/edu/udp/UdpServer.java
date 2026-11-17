package br.edu.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UdpServer implements AutoCloseable {
    public record Request(Message message, InetAddress clientHost, int clientPort, byte[] bytes) { }
    private record Key(InetAddress address, int port, int requestId) { }
    private record Cached(byte[] request, byte[] reply) { }
    private final DatagramSocket socket;
    private final Map<Key, Cached> cache = new LinkedHashMap<>();

    public UdpServer(String bindHost, int port) throws IOException {
        socket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(bindHost), port));
    }

    public int port() {
        return socket.getLocalPort();
    }

    public Request getRequest() throws IOException {
        while (true) {
            byte[] buffer = new byte[Protocol.MAX_DATAGRAM + 1];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            byte[] bytes = Arrays.copyOf(packet.getData(), packet.getLength());
            try {
                Message message = Protocol.decode(bytes);
                if (message.messageType() == Message.REQUEST) {
                    return new Request(message, packet.getAddress(), packet.getPort(), bytes);
                }
            } catch (IOException e) {
                // Nao ha cabecalho confiavel para responder a datagramas malformados.
            }
        }
    }

    public void sendReply(byte[] reply, InetAddress clientHost, int clientPort) throws IOException {
        socket.send(new DatagramPacket(reply, reply.length, clientHost, clientPort));
    }

    public void serve() throws IOException {
        while (!socket.isClosed()) {
            try {
                Request request = getRequest();
                Message message = request.message();
                Key key = new Key(request.clientHost(), request.clientPort(), message.requestId());
                Cached cached = cache.get(key);
                byte[] reply;
                if (cached != null) {
                    // Mesmo ID com outro conteudo nao deve executar uma segunda operacao.
                    if (!Arrays.equals(cached.request(), request.bytes())) {
                        continue;
                    }
                    reply = cached.reply();
                } else {
                    byte[] body;
                    try {
                        byte[] result = Calculator.execute(message.operationId(), message.arguments());
                        body = ByteBuffer.allocate(result.length + 1).put((byte) 0).put(result).array();
                        reply = replyFor(message, body);
                    } catch (IllegalArgumentException | IOException e) {
                        body = Calculator.error(e.getMessage());
                        reply = replyFor(message, body);
                    }
                    cache.put(key, new Cached(request.bytes(), reply));
                    if (cache.size() > 256) {
                        cache.remove(cache.keySet().iterator().next());
                    }
                }
                sendReply(reply, request.clientHost(), request.clientPort());
            } catch (SocketException e) {
                if (!socket.isClosed()) {
                    throw e;
                }
            }
        }
    }

    private static byte[] replyFor(Message request, byte[] body) throws IOException {
        return Protocol.encode(new Message(Message.REPLY, request.requestId(), request.remoteReference(),
                request.operationId(), body));
    }

    @Override
    public void close() {
        socket.close();
    }
}
