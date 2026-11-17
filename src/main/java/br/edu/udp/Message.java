package br.edu.udp;

public record Message(int messageType, int requestId, RemoteRef remoteReference,
                      int operationId, byte[] arguments) {
    public static final int REQUEST = 0;
    public static final int REPLY = 1;

    public Message {
        if (messageType != REQUEST && messageType != REPLY) {
            throw new IllegalArgumentException("Tipo de mensagem invalido.");
        }
        if (remoteReference == null || arguments == null) {
            throw new IllegalArgumentException("Referencia e argumentos sao obrigatorios.");
        }
        arguments = arguments.clone();
    }

    @Override
    public byte[] arguments() {
        return arguments.clone();
    }
}
