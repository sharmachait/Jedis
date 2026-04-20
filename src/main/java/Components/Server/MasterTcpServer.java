package Components.Server;

import Components.Infra.ConnectionPool;
import Components.Infra.Slave;
import Components.Repository.OptimisticLockException;
import Components.Repository.Store;
import Components.Repository.Value;
import Components.Service.CommandHandler;
import Components.Service.RespSerializer;
import Components.Infra.Client;
import Components.Service.ResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

@Component
public class MasterTcpServer {
    @Autowired
    private RespSerializer respSerializer;
    @Autowired
    private CommandHandler commandHandler;
    @Autowired
    private RedisConfig redisConfig;
    @Autowired
    private ConnectionPool connectionPool;
    @Autowired
    private Store store;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    public void startServer(){
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        int port = redisConfig.getPort();
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            int id = 0;
            while (true) {
                clientSocket = serverSocket.accept();
                id++;
                Socket finalClientSocket = clientSocket;

                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();

                Client client = new Client(finalClientSocket, inputStream, outputStream, id );
                executorService.submit(() -> {
                    try {
                        handleClient(client);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

        } catch (IOException e) {
          System.out.println(e.getMessage());
        } finally {
            try {
                executorService.shutdown();
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
              System.out.println(e.getMessage());
            }
        }
    }
    private void handleClient(Client client) throws IOException {
        connectionPool.addClient(client);
//        while(client.socket.isConnected()){
        while(true){
            byte[] buffer = new byte[client.socket.getReceiveBufferSize()];
            int bytesRead = client.inputStream.read(buffer);

            if(bytesRead > 0){
                // bytes parsing into strings
                List<String[]> commands = respSerializer.deseralize(buffer);

                for(String[] command :commands){
                    printCommand(command);
                    System.out.println(client.isSubscribed);
                    if(client.isSubscribed){
                        handleCommandSubscribed(command, client);
                    } else {
                        handleCommand(command, client);
                    }
                }
            }else if(bytesRead == -1){
              break;
            }
        }
        connectionPool.removeClient(client);
        connectionPool.removeSlave(client);
    }

    private void printCommand(String[] command){
      System.out.println("=========================");
      for(String s: command){
        System.out.print(s+" ");
      }
      System.out.println("=========================");
    }
    private void handleCommandSubscribed(String[] command, Client client) throws IOException {
      if(!isCommandSubscribeModeEligible(command[0].toUpperCase())){
            String errMessage = "-ERR Can't execute '"+command[0].toLowerCase()+"': only (P|S)SUBSCRIBE / (P|S)UNSUBSCRIBE / PING / QUIT / RESET are allowed in this context\r\n";
            client.send(errMessage);
            return;
        }
        String res = "";
        switch (command[0].toUpperCase()) {
          case "SUBSCRIBE":
            res = commandHandler.subscribe(command, client);
            break;
          default:
            res = command[0];
            break;
        }
        client.send(res);
    }
    private boolean isCommandSubscribeModeEligible(String command){
        return switch (command) {
          case "SUBSCRIBE", "UNSUBSCRIBE", "PSUBSCRIBE", "PUNSUBSCRIBE", "PING", "QUIT" -> true;
          default -> false;
        };
    }


    private void handleCommand(String[] command, Client client) throws IOException {
        if(!client.getTransactionalContext()){
            ResponseDto responseDto = caseHandler(command, client);
            client.send(responseDto);
        }else if(!isTransactionalControlCommand(command[0])){
            // we are in the transactional context and the command is a normal command
            addCommandToTransaction(command, client);
        }else{
            // we are in the transactional context and the command is a transaction control command, EXEC or DISCARD
            transactionController(command, client);
        }

    }

    private void transactionController(String[] command, Client client) throws IOException {
        //control only comes here in the transaction context
        switch (command[0].toUpperCase()){
            case "WATCH":
                String res = "-ERR WATCH inside MULTI is not allowed\r\n";
                client.send(res);
                break;
            case "EXEC":
                if(client.commandQueue==null || client.commandQueue.isEmpty()){
                    client.send("*0\r\n");
                    client.endTransaction();
                    return;
                }

                Queue<String[]> commands = new LinkedList<>(client.commandQueue);

                //execute the transaction
                BiFunction<String[], Map<String, Value>, String> transactionCacheApplier = commandHandler.getTransactionCommandCacheApplier(client);
                try{
                    store.executeTransaction(client, transactionCacheApplier);
                    while(!commands.isEmpty()){
                        String[] commandToPropagate = commands.poll();
                        String commandRespString = respSerializer.respArray(commandToPropagate);
                        byte[] toCount = commandRespString.getBytes();
                        connectionPool.bytesSentToSlaves += toCount.length;
                        CompletableFuture.runAsync(()->propagate(commandToPropagate));
                    }
                    String response = respSerializer.respArray(client.transactionResponse);
                    client.send(response);
                } catch(OptimisticLockException e) {
                  client.send("*-1\r\n");
                } finally {
                  client.endTransaction();
                }
                break;
            case "DISCARD":
                if(client.watchSet!=null){
                    for(String key: client.watchSet) {
                        store.removeWatcherForKey(key, client);
                    }
                }
                if(store.failTransactionFor.contains(client.id))
                    store.failTransactionFor.remove(client.id);

                client.endTransaction();
                client.send("+OK\r\n");
                break;
        }
    }

    private void addCommandToTransaction(String[] command, Client client) throws IOException {
        client.commandQueue.offer(command);
        client.send("+QUEUED\r\n");
    }

    private boolean isTransactionalControlCommand(String command) {
        return switch (command.toUpperCase()) {
            case "EXEC", "DISCARD", "WATCH" -> true;
            default -> false;
        };
    }

    public ResponseDto caseHandler(String[] command, Client client){
        //control comes here only when the client is not in a transaction
        String res = "";
        byte[] data = null;
        switch (command[0].toUpperCase()){
            case "PING":
                res = commandHandler.ping(command);
                break;
            case "EXEC":
                res = "-ERR EXEC without MULTI\r\n";
                break;
            case "DISCARD":
                res = "-ERR DISCARD without MULTI\r\n";
                break;
            case "UNWATCH":
                if(client.watchSet!=null){
                    for(String key: client.watchSet) {
                        store.removeWatcherForKey(key, client);
                    }
                }
                if(store.failTransactionFor.contains(client.id))
                    store.failTransactionFor.remove(client.id);
                client.watchSet = null;
                res = "+OK\r\n";
                break;
            case "WATCH":
                res = commandHandler.watch(command, client);
                break;
            case "MULTI":
                client.beginTransaction();
                res = "+OK\r\n";
                break;
            case "INCR":
                res = commandHandler.incr(command);
                break;
            case "ECHO":
                res = commandHandler.echo(command);
                break;
            case "SET":
                res = commandHandler.set(command);
                String commandRespString = respSerializer.respArray(command);
                byte[] toCount = commandRespString.getBytes();
                connectionPool.bytesSentToSlaves += toCount.length;
                CompletableFuture.runAsync(()->propagate(command));
                break;
            case "GET":
                res = commandHandler.get(command);
                break;
            case "INFO":
                res = commandHandler.info(command);
                break;
            case "REPLCONF":
                res = commandHandler.replconf(command, client);
                break;
            case "WAIT":
                if(connectionPool.bytesSentToSlaves == 0){
                    res = respSerializer.respInteger(connectionPool.slavesThatAreCaughtUp);
                    break;
                }
                Instant start = Instant.now();
                res = commandHandler.wait(command, start);
                connectionPool.slavesThatAreCaughtUp = 0;
                break;
            case "PSYNC":
                ResponseDto resDto = commandHandler.psync(command);
                res = resDto.response;
                data = resDto.data;
                break;
            case "SUBSCRIBE":
                res = commandHandler.subscribe(command, client);
                break;
        }
        return new ResponseDto(res, data);
    }


    private void propagate(String[] command) {
        String commandRespString = respSerializer.respArray(command);
        try{
            for(Slave slave: connectionPool.getSlaves()){
                InetAddress remoteAddress = slave.connection.socket.getInetAddress();

                slave.send(commandRespString.getBytes());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
