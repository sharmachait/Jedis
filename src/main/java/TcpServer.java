import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TcpServer{
    public TcpServer(){

    }
    public void StartMaster(){
        try {
            ServerSocket serverSocket = new ServerSocket(6379);
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

                CompletableFuture.runAsync(() -> handleClientAsync(clientSocket,inputStream,outputStream));
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
    public CompletableFuture<Void> handleClientAsync(Socket clientSocket,InputStream inputStream,OutputStream outputStream){
        RespParser parser = new RespParser();
        CommandHandler handler = new CommandHandler();
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] buffer = new byte[clientSocket.getReceiveBufferSize()];
                while(clientSocket.isConnected()){
                    int bytesRead = inputStream.read(buffer);
                    if(bytesRead > 0){
                        List<String[]> commands = parser.Deserialize(buffer);
                        for(String[] command : commands){
                            String response = handler.handle(command);
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