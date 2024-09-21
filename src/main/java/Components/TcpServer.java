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
                Socket clientSocket = serverSocket.accept();
                InetSocketAddress remoteIpEndPoint = (InetSocketAddress) clientSocket.getRemoteSocketAddress();
                if (remoteIpEndPoint == null)
                    return;
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();

                Client client = new Client(clientSocket,
                        remoteIpEndPoint,
                        inputStream,
                        outputStream,  id++ );
                infra.clients.add(client);
                CompletableFuture<Void> future = handleClientAsync(client);
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
    public CompletableFuture<Void> handleClientAsync(Client client){

        return CompletableFuture.runAsync(() -> {
            try {
                System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++===");
                System.out.println("starting master server");
                while(client.socket.isConnected()){
                    byte[] buffer = new byte[client.socket.getReceiveBufferSize()];
                    int bytesRead = client.inputStream.read(buffer);
                    if(bytesRead > 0){
                        List<String[]> commands = parser.Deserialize(buffer);
                        for(String[] command : commands){
                            //add a stopwatch// no need as of now handle in the command handler
                            for(String c:command){
                                System.out.println("============================================================");
                                System.out.print(c+" ");
                                System.out.println();
                            }
                            ResponseDTO response = commandHandler.handle(command, LocalDateTime.now(), client);
                            if(!response.response.equals(""))
                                client.send(response.response);
                            if(response.data != null){
                                client.send(response.data);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void StartSlave(){
        try{
            serverSocket = new ServerSocket(redisConfig.port);
            serverSocket.setReuseAddress(true);
            Thread slaveThread = new Thread(() -> {InitiateSlavery();});
            slaveThread.start();
            StartMasterForSlaveInstance();
        }catch(Exception e){
            System.out.println("IOException: " + e.getMessage());
        }
    }
    public void StartMasterForSlaveInstance(){
        try {
            while(true){
                Socket clientSocket = serverSocket.accept();
                InetSocketAddress remoteIpEndPoint = (InetSocketAddress) clientSocket.getRemoteSocketAddress();

                if (remoteIpEndPoint == null)
                    return;
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();

                Client client = new Client(clientSocket,
                        remoteIpEndPoint,
                        inputStream,
                        outputStream,  id++ );
                infra.clients.add(client);
                CompletableFuture<Void> future = handleClientAsync(client);
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
    public void InitiateSlavery(){
        try{
            Socket master = new Socket(redisConfig.masterHost, redisConfig.masterPort);
            StartListeningToMaster(master, master.getInputStream(),master.getOutputStream());
        }catch(Exception e){
            System.out.println("IOException: " + e.getMessage());
        }
    }
    public void StartListeningToMaster(Socket master, InputStream inputStream, OutputStream outputStream) throws IOException {
        int listeningPort = redisConfig.port;
        int lenListeningPort = (redisConfig.port+"").length();
        String[] handshakeParts = new String[]{
                "*1\r\n$4\r\nPING\r\n",
                "*3\r\n$8\r\nREPLCONF\r\n$14\r\nlistening-port\r\n$" +
                        (lenListeningPort+"") + "\r\n" + (listeningPort+"") +
                        "\r\n",
                "*3\r\n$8\r\nREPLCONF\r\n$4\r\ncapa\r\n$6\r\npsync2\r\n",
                "*3\r\n$5\r\nPSYNC\r\n$1\r\n?\r\n$2\r\n-1\r\n"
        };
        byte[] buffer = new byte[1024];
        int c=0;
        for(String part :handshakeParts){
            byte[] data = part.getBytes();
            outputStream.write(data);
            if(c==3)
                break;
            inputStream.read(buffer,0,buffer.length);
            c++;
        }
        List<Integer> psyncResponse = new ArrayList<>();
        while(true){
            if(inputStream.available()<=0)
                continue;

            int b = inputStream.read();
            psyncResponse.add(b);
            if(b==(int)'*'){
                break;
            }
        }
        while(master.isConnected()){
            int offset = 1;
            StringBuilder sb = new StringBuilder();
            List<Byte> bytes = new ArrayList<>();

            while(true){
                int b = inputStream.read();
                if(b==(int)'*')
                    break;

                offset++;
                bytes.add((byte)b);

                if(inputStream.available()<=0)
                    break;
            }

            for(Byte b : bytes)
                sb.append((char)(b.byteValue() & 0xFF));

            if(bytes.size()==0)
                continue;

            String command = sb.toString();
            String[] parts = command.split("\r\n");

            if (command.equals("+OK\r\n"))
                continue;

            String[] commandArray = parser.ParseArray(parts);
            String res = commandHandler.HandleCommandsFromMaster(commandArray,master);

            if (commandArray[0].equals("replconf") && commandArray[1].equals("GETACK")){
                outputStream.write(res.getBytes());
                offset++;
                List<Byte> leftOverCommand = new ArrayList<>();
                while(true){
                    if(inputStream.available()<=0)
                        break;
                    byte b = (byte)inputStream.read();
                    leftOverCommand.add(b);
                    if((int)b==(int)'*')
                        break;
                    offset++;
                }
                StringBuilder leftoverSB = new StringBuilder();
                for(Byte b : leftOverCommand)
                    leftoverSB.append((char)(b.byteValue() & 0xFF));
            }
            redisConfig.masterReplOffset+=offset;
        }
    }
}

















































