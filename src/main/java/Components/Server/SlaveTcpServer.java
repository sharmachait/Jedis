package Components.Server;

import Components.Infra.ConnectionPool;
import Components.Infra.Slave;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SlaveTcpServer {
    @Autowired
    private RespSerializer respSerializer;
    @Autowired
    private CommandHandler commandHandler;
    @Autowired
    private RedisConfig redisConfig;
    @Autowired
    private ConnectionPool connectionPool;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    public void startServer(){
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        int port = redisConfig.getPort();

        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);

            //CompletableFuture<Void> slaveConnectionFuture = CompletableFuture.runAsync(this::initiateSlavery);
            //slaveConnectionFuture.thenRun(()->System.out.println("Replication completed"));
            executorService.submit(()->{
             initiateSlavery();
            });
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
            executorService.shutdown();
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
              System.out.println(e.getMessage());
            }
        }
    }

    private void initiateSlavery() {
        try(Socket master = new Socket(redisConfig.getMasterHost(), redisConfig.getMasterPort())){
            InputStream inputStream = master.getInputStream();
            OutputStream outputStream = master.getOutputStream();
            byte[] inputBuffer = new byte[1024];

            //part 1 of the handshake
            byte[] data = "*1\r\n$4\r\nPING\r\n".getBytes();
            outputStream.write(data);
            int bytesRead = inputStream.read(inputBuffer,0,inputBuffer.length);
            String response = new String(inputBuffer,0,bytesRead, StandardCharsets.UTF_8);

            //part 2 of the handshake
            int lenListeningPort = (redisConfig.getPort()+"").length();
            int listeningPort = redisConfig.getPort();
            String replconf = "*3\r\n$8\r\nREPLCONF\r\n$14\r\nlistening-port\r\n$" +
                    (lenListeningPort+"") + "\r\n" + (listeningPort+"") +
                    "\r\n";
            data = replconf.getBytes();
            outputStream.write(data);
            bytesRead = inputStream.read(inputBuffer,0,inputBuffer.length);
            response = new String(inputBuffer,0,bytesRead, StandardCharsets.UTF_8);

            replconf = "*3\r\n$8\r\nREPLCONF\r\n$4\r\ncapa\r\n$6\r\npsync2\r\n";
            data = replconf.getBytes();
            outputStream.write(data);
            bytesRead = inputStream.read(inputBuffer,0,inputBuffer.length);
            response = new String(inputBuffer,0,bytesRead, StandardCharsets.UTF_8);

            // part 3 of the handshake
            String psync = "*3\r\n$5\r\nPSYNC\r\n$1\r\n?\r\n$2\r\n-1\r\n";
            data = psync.getBytes();
            outputStream.write(data);

            List<Integer> res = handlePsyncResponse(inputStream);

            // number of bytes in the input stream coming down from the master after this point, if they are read and the command is proccessed we can add
            // the number of bytes processes to the offset

            while(master.isConnected()){
                int offset = 1;
                StringBuilder sb = new StringBuilder();
                List<Byte> bytes = new ArrayList<>();

                while(true){
                    int b = inputStream.read();
                    if(b=='*'){
                        break;
                    }
                    offset++;
                    bytes.add((byte)b);
                    if(inputStream.available()<=0){
                        break;
                    }
                }

                for(Byte b: bytes){
                    sb.append((char)(b.byteValue() & 0xFF));
                }

                if(bytes.isEmpty())
                    continue;
                String command = sb.toString();
                String parts[] = command.split("\r\n");

                if(command.equals("+OK\r\n"))
                    continue;


                String[] commandArray = respSerializer.parseArray(parts);
                Client masterClient = new Client(master, master.getInputStream(), master.getOutputStream(), -1);
                String commandResult = handleCommandFromMaster(commandArray, masterClient);

                if(commandArray.length >= 2 && commandArray[0].equals("REPLCONF") && commandArray[1].equals("GETACK")){
                    if(!commandResult.equals("") && commandResult!=null)
                        outputStream.write(commandResult.getBytes());
                    offset++;
                    List<Byte> leftOverBytes = new ArrayList<>();
                    while(true){
                        if(inputStream.available()<=0)
                            break;
                        byte b = (byte)inputStream.read();
                        leftOverBytes.add(b);
                        if((int) b == (int)'*')
                            break;
                        offset++;
                    }
                    StringBuilder leftOverSb = new StringBuilder();
                    for(Byte b: leftOverBytes){
                        leftOverSb.append((char)(b.byteValue() & 0xFF));
                    }
                }
                redisConfig.setMasterReplOffset(offset + redisConfig.getMasterReplOffset());
            }

        } catch (Exception e) {
          System.out.println(e.getMessage());
        }
    }

    private String handleCommandFromMaster(String[] command, Client master) {
        String cmd = command[0];
        cmd = cmd.toUpperCase();

        String res = "";
        switch (cmd.toUpperCase()){
            case "SET":
                commandHandler.set(command);
                String commandRespString = respSerializer.respArray(command);
                byte[] toCount = commandRespString.getBytes();
                connectionPool.bytesSentToSlaves += toCount.length;
                CompletableFuture.runAsync(()->propagate(command));
                break;
            case "LPUSH":
                commandHandler.lpush(command);
                String commandRespStringLpush = respSerializer.respArray(command);
                byte[] toCountLpush = commandRespStringLpush.getBytes();
                connectionPool.bytesSentToSlaves += toCountLpush.length;
                CompletableFuture.runAsync(()->propagate(command));
                break;
            case "RPUSH":
                commandHandler.rpush(command);
                String commandRespStringRpush = respSerializer.respArray(command);
                byte[] toCountRpush = commandRespStringRpush.getBytes();
                connectionPool.bytesSentToSlaves += toCountRpush.length;
                CompletableFuture.runAsync(()->propagate(command));
                break;
            case "XADD":
                res = commandHandler.xadd(command);
                String commandRespStringXadd = respSerializer.respArray(command);
                byte[] toCountXadd = commandRespStringXadd.getBytes();
                connectionPool.bytesSentToSlaves += toCountXadd.length;
                CompletableFuture.runAsync(()->propagate(command));
                break;
            case "REPLCONF":
                res = commandHandler.replconf(command, master);
                break;
        }
        return res;
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

    private List<Integer> handlePsyncResponse(InputStream inputStream) throws IOException {
        List<Integer> res = new ArrayList<>();
        while(true){
            if(inputStream.available() <= 0)
                continue;
            int b = inputStream.read();
            res.add(b);
            if(b == (int)'*') {
                break;
            }
        }
        return res;
    }

    private void handleClient(Client client) throws IOException {
        connectionPool.addClient(client);
        //while(client.socket.isConnected()){
        while(true){
            byte[] buffer = new byte[client.socket.getReceiveBufferSize()];
            int bytesRead = client.inputStream.read(buffer);

            if(bytesRead > 0){
                // bytes parsing into strings
                List<String[]> commands = respSerializer.deseralize(buffer);

                for(String[] command :commands){                    
                    if(client.isSubscribed){
                        handleCommandSubscribed(command, client);
                    } else {
                        handleCommand(command, client);
                    }
                }
            } else if(bytesRead == -1) {
              // Client disconnected;
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
          case "UNSUBSCRIBE":
            res = commandHandler.unsubscribe(command, client);
            break;
          case "PING":
            res = "*2\r\n$4\r\npong\r\n$0\r\n\r\n";
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
        String res = "";
        byte[] data = null;
        switch (command[0].toUpperCase()){
            case "PING":
                res = commandHandler.ping(command);
                break;
            case "ECHO":
                res = commandHandler.echo(command);
                break;
            case "SET":
                res = "-READONLY You can't write against a replica.\r\n";
                break;
            case "GET":
                res = commandHandler.get(command);
                break;
            case "LLEN": 
                res = commandHandler.llen(command);
                break;
            case "LRANGE":
                res = commandHandler.lrange(command);
                break;
            case "XREAD":
                res = commandHandler.xread(command);
                break;
            case "XRANGE":
                res = commandHandler.xrange(command);
                break;
            case "INFO":
                res = commandHandler.info(command);
                break;
            case "TYPE":
                res = commandHandler.type(command);
                break;
            case "PSYNC":
                ResponseDto resDto = commandHandler.psync(command);
                res = resDto.response;
                data = resDto.data;
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
            case "SUBSCRIBE":
                res = commandHandler.subscribe(command, client);
                break;
            case "UNSUBSCRIBE":
                res = commandHandler.unsubscribe(command, client);
                break;

        }
        client.send(res, data);
    }
}
