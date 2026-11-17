package br.edu.udp;

import java.io.IOException;

public final class RemoteOperationException extends IOException {
    private static final long serialVersionUID = 1L;

    public RemoteOperationException(String message) {
        super(message);
    }
}
