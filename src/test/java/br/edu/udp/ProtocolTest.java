package br.edu.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Testes executaveis somente com o JDK, sem bibliotecas adicionais. */
public final class ProtocolTest {
    @FunctionalInterface
    private interface Checked { void run() throws Exception; }
    private static int passed;

    public static void main(String[] args) throws Exception {
        test("serializacao e copia defensiva", ProtocolTest::serialization);
        test("cabecalhos invalidos e comprimentos inconsistentes", ProtocolTest::invalidFrames);
        test("limites de tamanho", ProtocolTest::sizeLimits);
        test("operacoes, eco UTF-8 e erros remotos", ProtocolTest::operations);
        test("datagramas invalidos nao interrompem o servidor", ProtocolTest::serverSurvivesBadPackets);
        test("duplicatas, conflito de ID e portas de clientes", ProtocolTest::duplicates);
        test("timeout e quantidade de retransmissoes", ProtocolTest::timeout);
        test("perda de requisicao seguida de retransmissao", ProtocolTest::retry);
        test("perda de resposta e reenvio pelo cache", ProtocolTest::lostReply);
        test("respostas incorretas e origem diferente sao ignoradas", ProtocolTest::correlation);
        test("clientes simultaneos", ProtocolTest::concurrentClients);
        test("encerramento libera a porta", ProtocolTest::shutdown);
        System.out.println("OK: " + passed + " grupos de testes passaram.");
    }

    private static void test(String name, Checked test) throws Exception {
        test.run();
        passed++;
        System.out.println("PASS: " + name);
    }

    private static void check(boolean condition) {
        if (!condition) { throw new AssertionError("Verificacao falhou."); }
    }

    private static void expect(Class<? extends Throwable> type, Checked operation) throws Exception {
        try {
            operation.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) { return; }
            throw new AssertionError("Esperado " + type.getSimpleName() + ", recebido " + e, e);
        }
        throw new AssertionError("Esperado " + type.getSimpleName());
    }

    private static Message request(int id, int port, int operation, byte[] payload) {
        return new Message(Message.REQUEST, id, new RemoteRef("127.0.0.1", port), operation, payload);
    }

    private static void serialization() throws Exception {
        byte[] payload = {0, -1, 42};
        Message original = request(-7, 9000, 5, payload);
        payload[0] = 9;
        Message decoded = Protocol.decode(Protocol.encode(original));
        check(decoded.messageType() == 0 && decoded.requestId() == -7 && decoded.operationId() == 5);
        check(decoded.remoteReference().equals(original.remoteReference()));
        check(Arrays.equals(decoded.arguments(), new byte[]{0, -1, 42}));
        decoded.arguments()[0] = 8;
        check(decoded.arguments()[0] == 0);
    }

    private static void invalidFrames() throws Exception {
        byte[] valid = Protocol.encode(request(1, 9000, 5, new byte[]{42}));
        expect(IOException.class, () -> Protocol.decode(new byte[0]));
        for (int end = 0; end < valid.length; end++) {
            byte[] cut = Arrays.copyOf(valid, end);
            expect(IOException.class, () -> Protocol.decode(cut));
        }
        byte[] badType = valid.clone();
        ByteBuffer.wrap(badType).putInt(9);
        expect(IOException.class, () -> Protocol.decode(badType));
        byte[] badHostLength = valid.clone();
        ByteBuffer.wrap(badHostLength).putShort(8, (short) 256);
        expect(IOException.class, () -> Protocol.decode(badHostLength));
        byte[] badUtf8 = valid.clone();
        badUtf8[10] = (byte) 0xff;
        expect(IOException.class, () -> Protocol.decode(badUtf8));
        byte[] badPort = valid.clone();
        ByteBuffer.wrap(badPort).putInt(19, 0);
        expect(IOException.class, () -> Protocol.decode(badPort));
        byte[] badPayload = valid.clone();
        ByteBuffer.wrap(badPayload).putInt(27, -1);
        expect(IOException.class, () -> Protocol.decode(badPayload));
        expect(IOException.class, () -> Protocol.decode(Arrays.copyOf(valid, valid.length + 1)));
        expect(IllegalArgumentException.class, () -> new UdpClient(0, 1));
        expect(IllegalArgumentException.class, () -> new RemoteRef("", 9000));
    }

    private static void sizeLimits() throws Exception {
        int maximum = Protocol.MAX_DATAGRAM - 31; // cabecalho + host 127.0.0.1
        byte[] encoded = Protocol.encode(request(1, 9000, 5, new byte[maximum]));
        check(encoded.length == Protocol.MAX_DATAGRAM);
        check(Protocol.decode(encoded).arguments().length == maximum);
        expect(IOException.class, () -> Protocol.encode(request(1, 9000, 5, new byte[maximum + 1])));
        expect(IOException.class, () -> Protocol.decode(new byte[Protocol.MAX_DATAGRAM + 1]));
    }

    private static final class RunningServer implements AutoCloseable {
        final UdpServer server = new UdpServer("127.0.0.1", 0);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<?> task = executor.submit(() -> { server.serve(); return null; });

        RunningServer() throws IOException { }

        RemoteRef remote() { return new RemoteRef("127.0.0.1", server.port()); }

        @Override
        public void close() throws Exception {
            server.close();
            try { task.get(3, TimeUnit.SECONDS); }
            finally { executor.shutdownNow(); }
        }
    }

    private static void operations() throws Exception {
        try (RunningServer running = new RunningServer()) {
            UdpClient client = new UdpClient(500, 3);
            double[] expected = {15, 9, 36, 4};
            for (int operation = 1; operation <= 4; operation++) {
                byte[] reply = client.doOperation(running.remote(), operation, Calculator.numbers(12, 3));
                check(ByteBuffer.wrap(reply).getDouble() == expected[operation - 1]);
            }
            byte[] text = "invocação remota: ação e café".getBytes(StandardCharsets.UTF_8);
            check(Arrays.equals(client.doOperation(running.remote(), 5, text), text));
            check(client.doOperation(running.remote(), 5, new byte[0]).length == 0);
            expect(RemoteOperationException.class, () -> client.doOperation(running.remote(), 4, Calculator.numbers(1, 0)));
            expect(RemoteOperationException.class, () -> client.doOperation(running.remote(), 99, new byte[0]));
            expect(RemoteOperationException.class, () -> client.doOperation(running.remote(), 1, new byte[2]));
            expect(RemoteOperationException.class, () -> client.doOperation(running.remote(), 1, Calculator.numbers(Double.NaN, 1)));
            expect(RemoteOperationException.class, () -> client.doOperation(running.remote(), 3, Calculator.numbers(Double.MAX_VALUE, 2)));
            // A requisicao cabe, mas a resposta de eco precisaria de um byte a mais.
            expect(RemoteOperationException.class, () -> client.doOperation(running.remote(), 5, new byte[8161]));
        }
    }

    private static DatagramPacket receive(DatagramSocket socket) throws IOException {
        byte[] data = new byte[65535];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        socket.receive(packet);
        return packet;
    }

    private static byte[] bytes(DatagramPacket packet) {
        return Arrays.copyOf(packet.getData(), packet.getLength());
    }

    private static void send(DatagramSocket socket, byte[] data, int port) throws IOException {
        socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), port));
    }

    private static void serverSurvivesBadPackets() throws Exception {
        try (RunningServer running = new RunningServer(); DatagramSocket socket = new DatagramSocket()) {
            send(socket, new byte[]{1, 2}, running.server.port());
            send(socket, new byte[Protocol.MAX_DATAGRAM + 100], running.server.port());
            Message reply = new Message(1, 5, running.remote(), 5, new byte[]{0});
            send(socket, Protocol.encode(reply), running.server.port());
            check(new UdpClient(500, 3).doOperation(running.remote(), 5, new byte[]{42})[0] == 42);
        }
    }

    private static void duplicates() throws Exception {
        try (RunningServer running = new RunningServer(); DatagramSocket a = new DatagramSocket();
             DatagramSocket b = new DatagramSocket()) {
            a.setSoTimeout(300); b.setSoTimeout(1000);
            byte[] request = Protocol.encode(request(7, running.server.port(), 5, new byte[]{42}));
            send(a, request, running.server.port());
            byte[] first = bytes(receive(a));
            send(a, request, running.server.port());
            check(Arrays.equals(first, bytes(receive(a))));
            byte[] conflicting = Protocol.encode(request(7, running.server.port(), 5, new byte[]{9}));
            send(a, conflicting, running.server.port());
            expect(SocketTimeoutException.class, () -> receive(a));
            send(b, conflicting, running.server.port());
            check(Protocol.decode(bytes(receive(b))).arguments()[1] == 9);
        }
    }

    private static void timeout() throws Exception {
        try (DatagramSocket blackhole = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            blackhole.setSoTimeout(500);
            UdpClient client = new UdpClient(70, 3);
            expect(SocketTimeoutException.class, () -> client.doOperation(
                    new RemoteRef("127.0.0.1", blackhole.getLocalPort()), 5, new byte[0]));
            byte[] first = bytes(receive(blackhole));
            check(Arrays.equals(first, bytes(receive(blackhole))));
            check(Arrays.equals(first, bytes(receive(blackhole))));
            blackhole.setSoTimeout(80);
            expect(SocketTimeoutException.class, () -> receive(blackhole));
        }
    }

    private static void reply(DatagramSocket socket, DatagramPacket destination, Message message) throws IOException {
        byte[] wire = Protocol.encode(message);
        socket.send(new DatagramPacket(wire, wire.length, destination.getAddress(), destination.getPort()));
    }

    private static Message success(Message request, byte value) {
        return new Message(1, request.requestId(), request.remoteReference(), request.operationId(), new byte[]{0, value});
    }

    private static void retry() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (DatagramSocket peer = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            peer.setSoTimeout(2000);
            Future<?> task = executor.submit(() -> {
                byte[] first = bytes(receive(peer)); // simula perda antes de processar
                DatagramPacket second = receive(peer);
                check(Arrays.equals(first, bytes(second)));
                reply(peer, second, success(Protocol.decode(first), (byte) 42));
                return null;
            });
            byte[] result = new UdpClient(100, 3).doOperation(new RemoteRef("127.0.0.1", peer.getLocalPort()), 5, new byte[]{42});
            check(result[0] == 42);
            task.get(3, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    private static void lostReply() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (RunningServer running = new RunningServer(); DatagramSocket proxy = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            proxy.setSoTimeout(2000);
            Future<?> task = executor.submit(() -> {
                DatagramPacket first = receive(proxy);
                send(proxy, bytes(first), running.server.port());
                byte[] dropped = bytes(receive(proxy));
                DatagramPacket repeated = receive(proxy);
                check(Arrays.equals(bytes(first), bytes(repeated)));
                send(proxy, bytes(repeated), running.server.port());
                byte[] cached = bytes(receive(proxy));
                check(Arrays.equals(dropped, cached));
                proxy.send(new DatagramPacket(cached, cached.length, first.getAddress(), first.getPort()));
                return null;
            });
            byte[] result = new UdpClient(200, 3).doOperation(new RemoteRef("127.0.0.1", proxy.getLocalPort()), 5, new byte[]{42});
            check(result[0] == 42);
            task.get(3, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    private static void correlation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (DatagramSocket peer = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
             DatagramSocket stranger = new DatagramSocket()) {
            peer.setSoTimeout(2000);
            Future<?> task = executor.submit(() -> {
                DatagramPacket packet = receive(peer);
                Message request = Protocol.decode(bytes(packet));
                reply(stranger, packet, success(request, (byte) 99));
                peer.send(new DatagramPacket(new byte[]{1}, 1, packet.getAddress(), packet.getPort()));
                reply(peer, packet, new Message(1, request.requestId() + 1, request.remoteReference(), 5, new byte[]{0, 99}));
                reply(peer, packet, new Message(0, request.requestId(), request.remoteReference(), 5, new byte[]{0, 99}));
                reply(peer, packet, new Message(1, request.requestId(), request.remoteReference(), 2, new byte[]{0, 99}));
                reply(peer, packet, new Message(1, request.requestId(), new RemoteRef("localhost", peer.getLocalPort()), 5, new byte[]{0, 99}));
                reply(peer, packet, new Message(1, request.requestId(), request.remoteReference(), 5, new byte[]{7}));
                reply(peer, packet, success(request, (byte) 42));
                return null;
            });
            check(new UdpClient(500, 3).doOperation(new RemoteRef("127.0.0.1", peer.getLocalPort()), 5, new byte[0])[0] == 42);
            task.get(3, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    private static void concurrentClients() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try (RunningServer running = new RunningServer()) {
            var tasks = new java.util.ArrayList<Future<Double>>();
            for (int i = 0; i < 24; i++) {
                final int value = i;
                tasks.add(executor.submit(() -> ByteBuffer.wrap(new UdpClient(500, 3)
                        .doOperation(running.remote(), 1, Calculator.numbers(value, 10))).getDouble()));
            }
            for (int i = 0; i < tasks.size(); i++) { check(tasks.get(i).get(3, TimeUnit.SECONDS) == i + 10); }
        } finally { executor.shutdownNow(); }
    }

    private static void shutdown() throws Exception {
        RunningServer running = new RunningServer();
        int port = running.server.port();
        running.close();
        try (DatagramSocket socket = new DatagramSocket(port, InetAddress.getByName("127.0.0.1"))) {
            check(socket.getLocalPort() == port);
        }
    }
}
