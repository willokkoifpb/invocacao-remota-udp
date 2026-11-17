package br.edu.udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Main {
    private static final Map<String, Integer> OPERATIONS = Map.of(
            "somar", 1, "subtrair", 2, "multiplicar", 3, "dividir", 4, "eco", 5);

    private Main() { }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                throw new IllegalArgumentException("Informe servidor ou cliente.");
            }
            if (args[0].equals("servidor") && (args.length == 2 || args.length == 3)) {
                int port = Integer.parseInt(args[1]);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Porta deve estar entre 1 e 65535.");
                }
                String bind = args.length == 3 ? args[2] : "127.0.0.1";
                try (UdpServer server = new UdpServer(bind, port)) {
                    Runtime.getRuntime().addShutdownHook(new Thread(server::close));
                    System.out.println("Servidor UDP em " + bind + ":" + server.port());
                    server.serve();
                }
            } else if (args[0].equals("cliente") && args.length >= 5) {
                RemoteRef remote = new RemoteRef(args[1], Integer.parseInt(args[2]));
                Integer operation = OPERATIONS.get(args[3]);
                if (operation == null) {
                    throw new IllegalArgumentException("Operacao desconhecida: " + args[3]);
                }
                byte[] input;
                if (operation == 5 && args.length == 5) {
                    input = args[4].getBytes(StandardCharsets.UTF_8);
                } else if (operation != 5 && args.length == 6) {
                    input = Calculator.numbers(Double.parseDouble(args[4]), Double.parseDouble(args[5]));
                } else {
                    throw new IllegalArgumentException("Quantidade de argumentos incorreta.");
                }
                byte[] output = new UdpClient(1000, 3).doOperation(remote, operation, input);
                if (operation != 5 && output.length != 8) {
                    throw new IOException("Resposta numerica deve ter 8 bytes.");
                }
                System.out.println("Resultado: " + (operation == 5
                        ? new String(output, StandardCharsets.UTF_8) : ByteBuffer.wrap(output).getDouble()));
            } else {
                throw new IllegalArgumentException("Comando invalido.");
            }
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Erro: " + e.getMessage());
            System.err.println("Uso: java -jar build/udp.jar servidor <porta> [host-bind]");
            System.err.println("     java -jar build/udp.jar cliente <host> <porta> <somar|subtrair|multiplicar|dividir> <a> <b>");
            System.err.println("     java -jar build/udp.jar cliente <host> <porta> eco <texto>");
            System.exit(1);
        }
    }
}
