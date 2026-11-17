# Invocação remota por UDP

Implementação do protocolo de requisição e resposta da atividade de Sistemas Distribuídos. O cliente envia o identificador da operação e os argumentos em bytes; o servidor executa a operação e devolve uma resposta com o mesmo `requestId`.

O exemplo de aplicação é uma calculadora com quatro operações e um serviço de eco. A comunicação usa `DatagramSocket` e `DatagramPacket`, sem RMI, HTTP ou bibliotecas de RPC.

## Requisitos

- JDK 17 ou superior, com `java`, `javac` e `jar` no PATH ou `JAVA_HOME` apontando para o JDK.
- PowerShell no Windows ou um shell POSIX no Linux/macOS.
- Python 3.10 ou superior apenas para o teste opcional com a segunda implementação.

Confira `java -version` e `javac -version`. Ambos devem indicar a versão 17 ou superior. O projeto não usa Maven nem precisa baixar dependências.

## Compilar

```sh
git clone https://github.com/willokkoifpb/invocacao-remota-udp.git
cd invocacao-remota-udp
```

No Windows, execute no PowerShell:

```powershell
./build.ps1
```

No Linux/macOS:

```sh
sh build.sh
```

O resultado fica em `build/udp.jar`. Os scripts usam o JDK de `JAVA_HOME`, quando definido. Os comandos abaixo pressupõem que `java` no PATH seja da mesma versão; se necessário, use o executável de `JAVA_HOME/bin`.

## Instanciar servidor e cliente

Abra dois terminais na pasta do projeto. No primeiro:

```sh
java -jar build/udp.jar servidor 9000
```

Saída: `Servidor UDP em 127.0.0.1:9000`.

No segundo:

```sh
java -jar build/udp.jar cliente 127.0.0.1 9000 somar 12 3
java -jar build/udp.jar cliente 127.0.0.1 9000 subtrair 12 3
java -jar build/udp.jar cliente 127.0.0.1 9000 multiplicar 12 3
java -jar build/udp.jar cliente 127.0.0.1 9000 dividir 12 3
java -jar build/udp.jar cliente 127.0.0.1 9000 eco "mensagem de teste"
```

Resultados esperados: `15.0`, `9.0`, `36.0`, `4.0` e `mensagem de teste`, com o prefixo `Resultado:`. Para encerrar o servidor, use `Ctrl+C`.

Divisão por zero, operação desconhecida e argumentos numéricos inválidos retornam erro. O cliente termina com código 1 em falhas; com código 0 quando a chamada é concluída.

## Testes automáticos

Windows:

```powershell
./test.ps1
```

Linux/macOS:

```sh
sh test.sh
```

Os scripts compilam a aplicação e executam 12 grupos de testes com sockets UDP reais em `127.0.0.1`, usando portas livres escolhidas pelo sistema. Não é preciso iniciar o servidor manualmente.

A suíte verifica serialização, mensagens truncadas, limites de tamanho, cálculos, eco UTF-8, erros remotos, retransmissão, perda de resposta, duplicatas, conflitos de ID, correlação das respostas, origem dos datagramas, chamadas simultâneas e liberação da porta. Qualquer falha encerra o comando com erro.

Saída final esperada:

```text
OK: 12 grupos de testes passaram.
```

## Teste com outra implementação

`peer.py` implementa o formato binário em Python, usando somente `socket` e `struct`, sem importar código Java. Depois de compilar:

```sh
python peer.py --self-test
```

No Windows, `py -3 peer.py --self-test` também pode ser usado se o Python Launcher estiver instalado. O teste inicia e encerra seus próprios processos. Ele verifica sete cenários com cliente Python e servidor Java, além de uma soma com cliente Java e servidor Python.

Esse teste verifica interoperabilidade entre as duas implementações do repositório. O teste com a implementação de um colega, solicitado no segundo item da atividade, **ainda depende de execução conjunta**. Não há registro de um teste com colega realizado.

Para fazer esse teste em duas máquinas:

1. Combinem o formato descrito em [PROTOCOLO.md](PROTOCOLO.md), os IDs das operações e a representação dos argumentos. O slide define os campos, mas não determina a codificação binária.
2. Na máquina que será o servidor, execute `java -jar build/udp.jar servidor 9000 0.0.0.0`.
3. Na outra máquina, execute `java -jar build/udp.jar cliente <IP-do-servidor> 9000 somar 12 3`, substituindo `<IP-do-servidor>` pelo endereço real.
4. Para conferir um servidor compatível com o cliente Python, use `python peer.py --host <IP-do-servidor> --port 9000`.
5. Invertam os papéis: cliente deste projeto com servidor do colega e cliente do colega com servidor deste projeto. Confiram soma, eco e erro por divisão por zero.
6. Registrem data, nomes, versões ou commits das duas implementações, comandos usados e resultados observados.

As máquinas precisam ter conectividade UDP na porta escolhida. O endereço `127.0.0.1` só funciona na própria máquina. O protocolo é didático, sem autenticação nem criptografia, e deve ser usado em uma rede de laboratório.

## Organização

| Arquivo/classe | Responsabilidade |
| --- | --- |
| `RemoteRef` | Endereço e porta do serviço |
| `Message` e `Protocol` | Campos, empacotamento e desempacotamento |
| `UdpClient` | Primitiva `doOperation` e espera pela resposta |
| `UdpServer` | Primitivas `getRequest` e `sendReply`, despacho e cache |
| `Calculator` | Operações da aplicação |
| `Main` | Comandos de servidor e cliente |
| `ProtocolTest` | Testes Java sem dependências adicionais |
| `peer.py` | Segunda implementação para conferir interoperabilidade |

`getRequest()` retorna um registro com a mensagem e o endereço/porta de origem. É uma adaptação do retorno `byte[]` mostrado no slide para manter os dados necessários a `sendReply`. A resposta é enviada para a origem real do datagrama, nunca para um endereço de retorno informado no conteúdo.

O cliente espera até 1 segundo por tentativa e faz até 3 envios da mesma requisição. Só aceita uma resposta da origem esperada com tipo, ID, referência e operação correspondentes. O servidor guarda as últimas 256 respostas para reaproveitá-las em retransmissões. Esse cache é limitado e se perde no encerramento: não existe garantia de execução única após reinício ou descarte de uma entrada. A aplicação de exemplo usa operações sem efeitos persistentes.

## Referências

- Material da disciplina: *Sistemas Distribuídos - Invocação Remota de Métodos*, slides 19 a 26.
- [DatagramSocket no Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/DatagramSocket.html).
- [DatagramPacket no Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/DatagramPacket.html).
