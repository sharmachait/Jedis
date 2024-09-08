public class CommandHandler {
    public String handle(String[] command){
        String cmd = command[0];
        String res="";
        switch(cmd){
            case "ping":
                res="+PONG\r\n";
                break;
            default:
                res = "+No Response\r\n";
                break;
        }
        return res;
    }
}
