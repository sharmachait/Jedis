import java.nio.charset.StandardCharsets;
import java.util.*;
public class RespParser {
    public RespParser(){

    }
    public List<String[]> Deserialize(byte[] command){
        String _data = new String(command, StandardCharsets.UTF_8);
        String[] commands = _data.split("\\*");
        List<String[]> res = new ArrayList<String[]>();
        int i=0;
        for(String c : commands){
            if(i==0){
                i++;
                continue;
            }
            String[] parts = c.split("\r\n");
            String[] commandArray = ParseArray(parts);
            res.add(commandArray);
        }
        return res;
    }
    public String[] ParseArray(String[] parts){
        String len = parts[0];
        int length = Integer.parseInt(len);
        String[] _command = new String[length];
        _command[0]=parts[2].toLowerCase();
        int idx = 1;
        for(int i = 4; i<parts.length; i += 2){
            _command[idx++] = parts[i];
        }
        return _command;
    }
    public String RespBulkString(String response){
        return "$" + response.length() + "\r\n" + response + "\r\n";
    }

    public String RespArray(String[] a){
        List<String> res = new ArrayList<String>();

        int len = a.length;

        res.add("*"+len);
        for(String e : a){
            res.add("$" + e.length());
            res.add(e);
        }
        return String.join("\r\n", res) + "\r\n";
    }

    public String RespInteger(int i) {
        StringBuilder sb = new StringBuilder();
        sb.append(':').append(i).append("\r\n");
        return sb.toString();
    }

    public String RespRdbFile(String content) {
        byte[] bytes = Base64.getDecoder().decode(content);
        StringBuilder sb = new StringBuilder();
        sb.append('$').append(bytes.length).append("\r\n").append(content);
        return sb.toString();
    }
}
