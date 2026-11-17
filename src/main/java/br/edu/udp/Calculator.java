package br.edu.udp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class Calculator {
    private Calculator() { }

    public static byte[] execute(int operation, byte[] arguments) {
        if (operation == 5) {
            return arguments.clone();
        }
        if (operation < 1 || operation > 4) {
            throw new IllegalArgumentException("Operacao desconhecida: " + operation);
        }
        if (arguments.length != 16) {
            throw new IllegalArgumentException("A operacao precisa de dois doubles (16 bytes).");
        }
        ByteBuffer input = ByteBuffer.wrap(arguments);
        double a = input.getDouble();
        double b = input.getDouble();
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            throw new IllegalArgumentException("Use numeros finitos.");
        }
        if (operation == 4 && b == 0.0) {
            throw new IllegalArgumentException("Divisao por zero.");
        }
        double result = switch (operation) {
            case 1 -> a + b;
            case 2 -> a - b;
            case 3 -> a * b;
            case 4 -> a / b;
            default -> throw new IllegalStateException();
        };
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Resultado fora do intervalo de double.");
        }
        return ByteBuffer.allocate(8).putDouble(result).array();
    }

    public static byte[] numbers(double a, double b) {
        return ByteBuffer.allocate(16).putDouble(a).putDouble(b).array();
    }

    public static byte[] error(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(1 + bytes.length).put((byte) 1).put(bytes).array();
    }
}
