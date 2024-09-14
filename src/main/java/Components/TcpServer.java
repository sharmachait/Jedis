package Components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

                CompletableFuture<Void> future = handleClientAsync(clientSocket,inputStream,outputStream);
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
    public CompletableFuture<Void> handleClientAsync(Socket clientSocket,InputStream inputStream,OutputStream outputStream){

        return CompletableFuture.runAsync(() -> {
            try {
                byte[] buffer = new byte[clientSocket.getReceiveBufferSize()];
                while(clientSocket.isConnected()){
                    int bytesRead = inputStream.read(buffer);
                    if(bytesRead > 0){
                        List<String[]> commands = parser.Deserialize(buffer);
                        for(String[] command : commands){
                            String response = commandHandler.handle(command);
                            outputStream.write(response.getBytes());
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}