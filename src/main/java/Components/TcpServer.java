package Components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class TcpServer{
    private ServerSocket serverSocket;
    private final RespParser parser;
    private final CommandHandler commandHandler;
    private final RedisConfig redisConfig;
    private final Infra infra;
    private int id;
    @Autowired
    public TcpServer(RespParser parser, CommandHandler commandHandler, RedisConfig redisConfig, Infra infra) {
        this.parser = parser;
        this.commandHandler = commandHandler;
        this.redisConfig = redisConfig;
        this.infra = infra;
        this.id=0;
    }
    public void StartMaster(){
        try {
            serverSocket = new ServerSocket(redisConfig.port);
            serverSocket.setReuseAddress(true);

            while(true){
                System.out.println("Waiting for client on port 6379");
                Socket clientSocket = serverSocket.accept();
                System.out.println("client connected");
                InetSocketAddress remoteIpEndPoint = (InetSocketAddress) clientSocket.getRemoteSocketAddress();

                if (remoteIpEndPoint == null)
                    return;
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();


                    Client client = new Client(clientSocket,
                            remoteIpEndPoint,
                            inputStream,
                            outputStream,  id++ );
                CompletableFuture<Void> future = handleClientAsync(client);
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
    public CompletableFuture<Void> handleClientAsync(Client client){

        return CompletableFuture.runAsync(() -> {
            try {
                while(client.socket.isConnected()){
                    byte[] buffer = new byte[client.socket.getReceiveBufferSize()];
                    int bytesRead = client.inputStream.read(buffer);
                    if(bytesRead > 0){
                        List<String[]> commands = parser.Deserialize(buffer);
                        for(String[] command : commands){
                            System.out.println(command[0]+"++++++++++++++++++++++++++++++++++++++++++++++");
                            String response = commandHandler.handle(command, LocalDateTime.now(), client);
                            client.send(response);
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}