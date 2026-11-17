# Verificação da entrega

Execução local em 17/11/2026, no Windows, com Eclipse Temurin JDK 17.0.15.

## Suíte Java

Comando: `./test.ps1`.

```text
Gerado: build/udp.jar
PASS: serializacao e copia defensiva
PASS: cabecalhos invalidos e comprimentos inconsistentes
PASS: limites de tamanho
PASS: operacoes, eco UTF-8 e erros remotos
PASS: datagramas invalidos nao interrompem o servidor
PASS: duplicatas, conflito de ID e portas de clientes
PASS: timeout e quantidade de retransmissoes
PASS: perda de requisicao seguida de retransmissao
PASS: perda de resposta e reenvio pelo cache
PASS: respostas incorretas e origem diferente sao ignoradas
PASS: clientes simultaneos
PASS: encerramento libera a porta
OK: 12 grupos de testes passaram.
```

O código principal também foi compilado com `-Xlint:all -Werror`, sem avisos.

## Interoperabilidade Java/Python

Comando: `python peer.py --self-test`, com Python 3 do ambiente de execução e o mesmo JDK.

```text
PASS: cliente Python -> servidor remoto (7 cenarios)
PASS: cliente Java -> servidor Python (soma)
```

Os testes usaram sockets UDP reais na própria máquina. Os testes não comprovam conectividade entre duas máquinas físicas ou redes diferentes.

## Teste com colega

Pendente: não foi fornecida uma implementação externa. O roteiro de execução conjunta está no README. Após o teste, registrar aqui os participantes, data, commits, comandos e resultados efetivamente observados.
