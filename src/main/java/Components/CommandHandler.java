package Components;

import org.springframework.stereotype.Component;

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


    public String handle(String[] command){
        String cmd = command[0];
        String res="";
        switch(cmd){
            case "ping":
                res="+PONG\r\n";
                break;
            case "echo":
                res="+"+command[1]+"\r\n";
                break;
            default:
                res = "+No Response\r\n";
                break;
        }
        return res;
    }
}
