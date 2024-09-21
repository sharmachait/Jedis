package Components;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.sql.SQLOutput;
import java.util.*;

@Component
public class RespParser {
    public int getParts (char[] data, int i, String[] subArray){
        int k=0;
        while(i<data.length && k<subArray.length){
            if(data[i] == '$') {
                String partsLen = "";
                while (i < data.length && Character.isDigit(data[i])) {
                    partsLen += data[i];
                    i++;
                }
                String part = "";
                for(int j = 0;j<Integer.parseInt(partsLen);j++) {
                    part+=data[i++];
                }
                subArray[k++] = part;
            }
            else if(data[i] == '*') {
                break;
            }
        }

        return i;
    }
    public List<String[]> Deserialize(byte[] command){

        String _data = new String(command, StandardCharsets.UTF_8);
        char[] dataArr = _data.toCharArray();
        System.out.println("control reached here ======================================================");

//        String[] commands = _data.split("\\*");

        List<String[]> res = new ArrayList<>();
        int i=0;
        while(i<dataArr.length){
            char curr = dataArr[i];

            if(curr == '*'){
                String arrLen = "";
                i++;
                while(i<dataArr.length && Character.isDigit(dataArr[i])){
                    arrLen += dataArr[i];
                    i++;
                }
                if(dataArr[i] == '*'){
                    String nestedLen = "";
                    i++;
                    while(i<dataArr.length && Character.isDigit(dataArr[i])){
                        nestedLen += dataArr[i];
                        i++;
                    }
                    String[] subArray = new String[Integer.parseInt(nestedLen)];
                    i = getParts(dataArr, i, subArray);
                    res.add(subArray);
                }
                else{
                    String[] subArray = new String[Integer.parseInt(arrLen)];
                    i = getParts(dataArr, i, subArray);
                    res.add(subArray);
                }
            }
        }
        for(String[] a:res){
            System.out.println("``````````````````````````````````````````````````````````````````");
            for(String b:a){
                System.out.print(b+", ");
            }
            System.out.println("``````````````````````````````````````````````````````````````````");
        }
//        int i=0;
//        for(String c : commands){
//            if(i==0){
//                i++;
//                continue;
//            }
//            String[] parts = c.split("\r\n");
//            String[] commandArray = ParseArray(parts);
//            res.add(commandArray);
//        }
//        for(String []c : res ){
//            for(String cc:c){
//                System.out.print(cc+" ");
//            }
//        }
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

    public String sizeEncoding(){

        return "";
    }
    public String sizeDecoding(){
        return "";
    }
}
