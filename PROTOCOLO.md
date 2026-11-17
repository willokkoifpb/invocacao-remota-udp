# Formato dos datagramas

Cada mensagem ocupa um datagrama UDP de até 8192 bytes. Inteiros e números de ponto flutuante usam big-endian (ordem de rede). O protocolo tem o mesmo cabeçalho na requisição e na resposta.

| Ordem | Campo | Codificação |
| --- | --- | --- |
| 1 | `messageType` | int32: 0 = requisição, 1 = resposta |
| 2 | `requestId` | int32 com sinal; repetir nas retransmissões e na resposta |
| 3 | tamanho do host | uint16, entre 1 e 255 |
| 4 | host de `remoteReference` | bytes UTF-8, sem terminador |
| 5 | porta de `remoteReference` | int32, entre 1 e 65535 |
| 6 | `operationId` | int32 |
| 7 | tamanho de `arguments` | int32 não negativo |
| 8 | `arguments` | quantidade exata de bytes informada no campo anterior |

O cabeçalho ocupa `22 + tamanhoDoHost` bytes. Bytes extras, host inválido, tipos desconhecidos e mensagens truncadas são rejeitados. Não há serialização nativa de objetos Java, padding ou delimitadores textuais.

A referência remota identifica o serviço solicitado, inclusive na resposta. O servidor copia o host e a porta recebidos para permitir a correlação literal feita pelo cliente. O endereço de destino da resposta vem de `DatagramPacket.getAddress()` e `getPort()`.

## Operações da calculadora

| ID | Operação | Argumentos | Resultado de sucesso |
| --- | --- | --- | --- |
| 1 | somar | dois doubles IEEE 754 de 64 bits | um double de 64 bits |
| 2 | subtrair | dois doubles, na ordem a, b | a - b, como double |
| 3 | multiplicar | dois doubles | um double |
| 4 | dividir | dois doubles, na ordem a, b | a / b, como double |
| 5 | eco | bytes livres, inclusive vetor vazio | mesmos bytes |

Na resposta, `arguments` começa com um byte de status: `00` para sucesso ou `01` para erro. O restante é o resultado da tabela ou uma mensagem de erro em UTF-8. Esse byte é removido por `doOperation` antes de devolver o resultado para a aplicação. Uma mensagem de erro causa `RemoteOperationException` no cliente Java.

Para o host `127.0.0.1`, a requisição comporta até 8161 bytes de argumentos. O eco de sucesso comporta até 8160, devido ao byte de status. Uma requisição de eco maior recebe um erro, desde que o datagrama de entrada respeite o limite.

## Exemplo para conferir outra implementação

Requisição de soma `12 + 3`, ID `1`, referência `127.0.0.1:9000`:

```text
00000000                         messageType = 0
00000001                         requestId = 1
0009                             comprimento do host = 9
3132372e302e302e31               host = 127.0.0.1
00002328                         porta = 9000
00000001                         operationId = 1
00000010                         tamanho dos argumentos = 16
4028000000000000                 double 12.0
4008000000000000                 double 3.0
```

Na resposta, o tipo muda para `00000001`, o tamanho dos argumentos passa a `00000009` e o corpo é `00 402e000000000000` (sucesso e double `15.0`). Os demais campos são copiados.

## Repetição e falhas

- O cliente retransmite o mesmo datagrama, no mesmo socket, preservando o `requestId`.
- O servidor usa a chave `(IP de origem, porta de origem, requestId)`. Se já houver resposta e os bytes forem idênticos, reenvia a resposta guardada.
- Se a mesma chave chegar com outro conteúdo, o servidor ignora o datagrama.
- O cache guarda as últimas 256 requisições distintas, por ordem de inserção. Não há persistência ou garantia de execução única após a remoção da entrada.
- O servidor processa uma requisição por vez; vários clientes podem enviar chamadas, que são atendidas sequencialmente.
- O prazo de cada tentativa não é renovado por respostas inválidas. Ao esgotar as tentativas, o cliente informa timeout; erros de rede também podem ser reportados antes disso.
- Datagramas inválidos e mensagens do tipo resposta recebidas pelo servidor são ignorados. Erros de aplicação com cabeçalho válido recebem resposta de erro.
- Não há descoberta automática de serviços, fragmentação na aplicação, autenticação ou criptografia. Em redes cujo MTU seja menor que o datagrama, pode ocorrer fragmentação IP e perda. Os exemplos usam mensagens pequenas.
