package br.edu.udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class Protocol {
    public static final int MAX_DATAGRAM = 8192;
    private static final int FIXED_HEADER = 22;

    private Protocol() { }

    public static byte[] encode(Message message) throws IOException {
        byte[] host = message.remoteReference().host().getBytes(StandardCharsets.UTF_8);
        byte[] payload = message.arguments();
        int length = FIXED_HEADER + host.length + payload.length;
        if (length > MAX_DATAGRAM) {
            throw new IOException("Mensagem excede " + MAX_DATAGRAM + " bytes.");
        }
        return ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN)
                .putInt(message.messageType()).putInt(message.requestId())
                .putShort((short) host.length).put(host)
                .putInt(message.remoteReference().port()).putInt(message.operationId())
                .putInt(payload.length).put(payload).array();
    }

    public static Message decode(byte[] data) throws IOException {
        if (data.length < FIXED_HEADER + 1 || data.length > MAX_DATAGRAM) {
            throw new IOException("Tamanho de datagrama invalido.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int type = buffer.getInt();
        int id = buffer.getInt();
        int hostLength = Short.toUnsignedInt(buffer.getShort());
        if (hostLength < 1 || hostLength > 255 || buffer.remaining() < hostLength + 12) {
            throw new IOException("Comprimento de host invalido.");
        }
        byte[] hostBytes = new byte[hostLength];
        buffer.get(hostBytes);
        String host;
        try {
            host = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(hostBytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Host nao esta em UTF-8.", e);
        }
        int port = buffer.getInt();
        int operation = buffer.getInt();
        int payloadLength = buffer.getInt();
        if (payloadLength < 0 || payloadLength != buffer.remaining()) {
            throw new IOException("Comprimento de argumentos invalido.");
        }
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        try {
            return new Message(type, id, new RemoteRef(host, port), operation, payload);
        } catch (IllegalArgumentException e) {
            throw new IOException("Cabecalho invalido: " + e.getMessage(), e);
        }
    }
}
