"""Par UDP independente para conferir o formato binario descrito em PROTOCOLO.md."""
import argparse
import os
from pathlib import Path
import queue
import socket
import struct
import subprocess
import threading

ROOT = Path(__file__).resolve().parent


def encode(kind, request_id, host, port, operation, payload):
    name = host.encode("utf-8")
    wire = (struct.pack("!iiH", kind, request_id, len(name)) + name
            + struct.pack("!iii", port, operation, len(payload)) + payload)
    if not 1 <= len(name) <= 255 or not 1 <= port <= 65535 or len(wire) > 8192:
        raise ValueError("Mensagem fora dos limites")
    return wire


def decode(wire):
    if not 23 <= len(wire) <= 8192:
        raise ValueError("Tamanho invalido")
    kind, request_id, name_len = struct.unpack_from("!iiH", wire)
    if kind not in (0, 1) or not 1 <= name_len <= 255 or len(wire) < 22 + name_len:
        raise ValueError("Cabecalho invalido")
    host = wire[10:10 + name_len].decode("utf-8")
    port, operation, length = struct.unpack_from("!iii", wire, 10 + name_len)
    body = wire[22 + name_len:]
    if len(body) != length or not host.strip() or not 1 <= port <= 65535:
        raise ValueError("Comprimentos ou referencia invalidos")
    return kind, request_id, host, port, operation, body


def call(host, port, operation, payload, request_id):
    request = encode(0, request_id, host, port, operation, payload)
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.connect((host, port))
        sock.settimeout(2)
        sock.send(request)
        reply = decode(sock.recv(8193))
    if reply[:5] != (1, request_id, host, port, operation):
        raise AssertionError("Resposta sem correspondencia com a requisicao")
    if not reply[5] or reply[5][0] not in (0, 1):
        raise AssertionError("Status de resposta invalido")
    return reply[5][0], reply[5][1:]


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def check_server(host, port):
    for operation, expected in enumerate((15.0, 9.0, 36.0, 4.0), start=1):
        status, body = call(host, port, operation, struct.pack("!dd", 12, 3), operation)
        require(status == 0 and struct.unpack("!d", body)[0] == expected, "Calculo incorreto")
    text = "ação, café e comunicação remota".encode("utf-8")
    require(call(host, port, 5, text, 5) == (0, text), "Eco UTF-8 incorreto")
    status, body = call(host, port, 4, struct.pack("!dd", 1, 0), 6)
    require(status == 1 and b"zero" in body, "Divisao por zero nao retornou erro")
    status, _ = call(host, port, 99, b"", 7)
    require(status == 1, "Operacao inexistente nao retornou erro")
    print("PASS: cliente Python -> servidor remoto (7 cenarios)")


def java_command():
    if os.environ.get("JAVA_HOME"):
        return str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java"))
    return "java"


def self_test():
    jar = ROOT / "build" / "udp.jar"
    if not jar.is_file():
        raise RuntimeError("Execute build.ps1 ou build.sh primeiro")
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    command = [java_command(), "-jar", str(jar)]
    server = subprocess.Popen(command + ["servidor", str(port)], stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
    ready = queue.Queue()
    threading.Thread(target=lambda: ready.put(server.stdout.readline()), daemon=True).start()
    try:
        line = ready.get(timeout=8)
        require(line.startswith("Servidor UDP"), "Servidor Java nao iniciou: " + line)
        check_server("127.0.0.1", port)
    finally:
        server.terminate()
        try:
            server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait(timeout=5)
        server.stdout.close()

    # Esta ponta nao usa nenhuma classe Java para receber e montar a resposta.
    errors = queue.Queue()
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as peer:
        peer.bind(("127.0.0.1", 0))
        peer.settimeout(5)

        def answer_once():
            try:
                wire, address = peer.recvfrom(8193)
                kind, request_id, host, target_port, operation, payload = decode(wire)
                require(kind == 0 and operation == 1, "Esperada uma soma")
                a, b = struct.unpack("!dd", payload)
                reply = encode(1, request_id, host, target_port, operation, b"\x00" + struct.pack("!d", a + b))
                peer.sendto(reply, address)
            except Exception as error:
                errors.put(error)

        worker = threading.Thread(target=answer_once, daemon=True)
        worker.start()
        result = subprocess.run(command + ["cliente", "127.0.0.1", str(peer.getsockname()[1]), "somar", "12", "3"],
                                capture_output=True, text=True, timeout=8)
        worker.join(timeout=6)
        if not errors.empty():
            raise errors.get()
        require(not worker.is_alive(), "Servidor Python nao encerrou")
        require(result.returncode == 0 and result.stdout.strip() == "Resultado: 15.0",
                "Cliente Java falhou: " + result.stdout + result.stderr)
        print("PASS: cliente Java -> servidor Python (soma)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true", help="inicia os pares Java e Python localmente")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9000)
    args = parser.parse_args()
    if args.self_test:
        self_test()
    else:
        check_server(args.host, args.port)


if __name__ == "__main__":
    main()
