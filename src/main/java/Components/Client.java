package Components;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

public class Client {
    public Socket socket;
    public InetSocketAddress remoteIpEndPoint;
    public InputStream inputStream;
    public OutputStream outputStream;
    public int port;
    public String ipAddress;
    public int id;

    public Client(Socket socket,
                  InetSocketAddress remoteIpEndPoint,
                  InputStream inputStream,
                  OutputStream outputStream,
                  int id) {
        this.socket = socket;
        this.remoteIpEndPoint = remoteIpEndPoint;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.port = remoteIpEndPoint.getPort();
        this.ipAddress = remoteIpEndPoint.getAddress().getHostAddress();
        this.id = id;
    }
    public void send(String response){
        try{
            outputStream.write(response.getBytes());
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
    }

    public CompletableFuture<Void> sendAsync(String response){
        return CompletableFuture.runAsync(()->send(response));
    }

    public void send(byte[] response){
        try{
            outputStream.write(response);
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
    }

    public CompletableFuture<Void> sendAsync(byte[] response){
        return CompletableFuture.runAsync(()->send(response));
    }
}
