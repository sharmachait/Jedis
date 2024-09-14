package Components;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

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


    public String handle(String[] command, LocalDateTime curr, Client client){
        String cmd = command[0];
        String res="";
        switch(cmd){
            case "ping":
                res="+PONG\r\n";
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
                //CompletableFuture.runAsync(()->sendCommandToSlave(infra.slaves,command));
                break;
            default:
                res = "+No Response\r\n";
                break;
        }
        return res;
    }

    public String Set(Client client, String[] command){
        return store.Set(command);
    }
}
