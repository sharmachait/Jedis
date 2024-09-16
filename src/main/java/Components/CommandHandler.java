package Components;

import org.springframework.stereotype.Component;

import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class CommandHandler {


    private final RespParser parser;
    private final Store store;
    private final RedisConfig config;
    private final Infra infra;
    private int slaveId = 0;

    public CommandHandler(Store store, RespParser parser, RedisConfig config, Infra infra)
    {
        this.infra = infra;
        this.parser = parser;
        this.store = store;
        this.config = config;
    }


    public ResponseDTO handle(String[] command, LocalDateTime curr, Client client){
        String cmd = command[0];
        String res="";
        byte[] data=null;
        switch(cmd){
            case "ping":
                res="+PONG\r\n";
                break;
            case "config":
                res = config(command);
                break;
            case "echo":
                res="+"+command[1]+"\r\n";
                break;
            case "get":
                res = store.Get(command, curr);
                break;
            case "set":
                res = Set(client, command);
                String commandRespString = parser.RespArray(command);
                byte[] toCount = commandRespString.getBytes();
                infra.bytesSentToSlave += toCount.length;
                CompletableFuture.runAsync(()->sendCommandToSlaves(infra.slaves,command));
                break;
            case "info":
                res = info(command);
                break;
            case "replconf":
                res = ReplConf(command, client);
                break;
            case "wait":

                if(infra.bytesSentToSlave == 0){
                    res = parser.RespInteger(infra.slaves.size());
                    break;
                }
                Instant start = Instant.now();
                res = wait(command, client, start);
                System.out.println();
                System.out.println("------------------------------------------ slaves that responded positively in time");
                System.out.println(res);
                System.out.println();
                infra.slavesThatAreCaughtUp = 0;
                break;
            case "psync":
                ResponseDTO response = Psync(command,client);
                res= response.response;
                data = response.data;
                break;
            default:
                res = "+No Response\r\n";
                break;
        }
        return new ResponseDTO( res, data);
    }

    public String Set(Client client, String[] command){
        return store.Set(command);
    }
    public void sendCommandToSlaves(ConcurrentLinkedQueue<Slave> slaves, String[] command){
        for(Slave slave : slaves){
            String commandRespString = parser.RespArray(command);
            slave.connection.sendAsync(commandRespString);
        }
    }
    public String info(String[] command){
        switch (command[1]){
            case "replication":
                try{
                    return replication();
                }catch(Exception e){
                    return "Invalid Options";
                }
            default:
                return "Invalid Command";
        }
    }
    public String replication(){
        String role = "role:"+config.role;
        String masterReplid = "master_replid:"+config.masterReplId;
        String masterReplOffset = "master_repl_offset:"+config.masterReplOffset;
        String[] info=new String[]{role,masterReplid,masterReplOffset};
        String replicationData = String.join("\r\n", info);
        return parser.RespBulkString(replicationData);
    }
    public String config(String[] command){
        switch (command[1])
        {
            case "GET":
                switch (command[2])
                {
                    case "dir":
                        return parser.RespArray(new String[] {"dir", config.dir });
                    case "dbfilename":
                        return parser.RespArray(new String[] { "dir", config.dbfilename });
                    default:
                        return "invalid options";
                }
            default:
                return "invalid operation";
        }
    }
    public String ReplConf(String[] command, Client client){
        String clientAddress = client.remoteIpEndPoint.getAddress().getHostAddress();
        int clientPort = client.remoteIpEndPoint.getPort();
        switch(command[1]){
            case "listening-port":
                try{
                    Slave s = new Slave(++slaveId, client);
                    infra.slaves.add(s);

                    return "+OK\r\n";
                }catch(Exception e){
                    System.out.println(e.getMessage());
                    return "+NOTOK\r\n";
                }
            case "capa":
                try{
                    Slave slave = null;
                    for(Slave s : infra.slaves){
                        if(s.connection.ipAddress.equals(clientAddress)){
                            slave = s;
                            break;
                        }
                    }
                    for(int i=0;i<command.length;i++){
                        if(command[i].equals("capa")){
                            slave.capabilities.add(command[i+1]);
                        }
                    }
                    return "+OK\r\n";
                }catch(Exception e){
                    System.out.println(e.getMessage());
                    return "+NOTOK\r\n";
                }
            case "ACK":
                System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++ replconf ack");
                //throw new RuntimeException("handle the ack");
                infra.slaveAck(Integer.parseInt(command[2]));
                return "";
        }
        return "+OK\r\n";
    }
    public String wait(String[] command, Client client, Instant start){
        System.out.println();
        System.out.println("starting wait ---------------------------------------------------------");
        String[] getackarr = new String[] { "REPLCONF", "GETACK", "*" };
        String getack = parser.RespArray(getackarr);
        byte[] bytearr = getack.getBytes();
        int bufferSize = bytearr.length;

        int required = Integer.parseInt(command[1]);
        int time = Integer.parseInt(command[2]);

        for(Slave slave: infra.slaves){
            CompletableFuture.runAsync(()->{slave.connection.sendAsync(getack);});
        }
        int res = 0;
        System.out.println("waiting for "+time +"dkfjhgfdkjhgkjdfhgkjfdhgkjdfhgkjdhggkjdhgkjdhgkjdhgkjdhgkjdhgd");
        while(true){
            if(res>=required)
                break;
            if(Duration.between(Instant.now(), start).toMillis() >= time)
                break;
            res= infra.slavesThatAreCaughtUp;
        }
        System.out.println("ending wait ---------------------------------------------------------");
        infra.bytesSentToSlave+=bufferSize;
        if(res > required)
            return parser.RespInteger(required);
        return parser.RespInteger(res);
    }
    public ResponseDTO Psync(String[] command, Client client) {
        try {
            String clientIpAddress = client.remoteIpEndPoint.getAddress().getHostAddress();
            int clientPort = client.remoteIpEndPoint.getPort();

            String replicationIdMaster = command[1];
            String replicationOffsetMaster = command[2];

            if (replicationIdMaster.equals("?") && replicationOffsetMaster.equals("-1")) {
                String emptyRdbFileBase64 =
                        "UkVESVMwMDEx+glyZWRpcy12ZXIFNy4yLjD6CnJlZGlzLWJpdHPAQPoFY3RpbWXCbQi8ZfoIdXNlZC1tZW3CsMQQAPoIYW9mLWJhc2XAAP/wbjv+wP9aog==";

                byte[] rdbFile = Base64.getDecoder().decode(emptyRdbFileBase64);  // Decode the Base64 string

                byte[] rdbResynchronizationFileMsg =
                        ("$" + rdbFile.length + "\r\n").getBytes("ASCII");  // ASCII encoding for header
                rdbResynchronizationFileMsg = concatenate(rdbResynchronizationFileMsg, rdbFile);  // Concatenate the arrays

                String res = "+FULLRESYNC "  + config.masterReplId + " " + config.masterReplOffset + "\r\n";
                infra.slavesThatAreCaughtUp++;
                return new ResponseDTO(res, rdbResynchronizationFileMsg);
            } else {
                return new ResponseDTO("Options not supported");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return new ResponseDTO("Options not supported");
        }
    }

    // Helper method to concatenate byte arrays
    private byte[] concatenate(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
    public String HandleCommandsFromMaster(String[] command, Socket ConnectionWithMaster)
    {
        String cmd = command[0];
        String res = "";
        Instant start = Instant.now();
        switch (cmd)
        {
            case "set":
                res = store.Set(command);
                CompletableFuture.runAsync(()->sendCommandToSlaves(infra.slaves,command));
                break;

            case "ping":
                System.out.println("-------------------------------------------");
                System.out.println("pinged");
                break;

            case "replconf":
                res = ReplConfSlave(command);
                break;

            default:
                res = "+No Response\r\n";
                break;
        }

        return res;
    }
    public String ReplConfSlave(String[] command)
    {
        String res = "";
        switch (command[1])
        {
            case "GETACK":
                res = parser.RespArray(
                        new String[] { "REPLCONF", "ACK", config.masterReplOffset+"" }
                );
                break;

            default:
                res = "Invalid options";
                break;
        }

        return res;
    }
}
