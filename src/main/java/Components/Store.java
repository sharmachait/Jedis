package Components;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Store {

    public ConcurrentHashMap<String,Value> map;

    public Store() {
        map = new ConcurrentHashMap<>();
    }

    public List<String> getKeys(){
        return new ArrayList<>(map.keySet());
    }

    public String Set(String[] command){
        try{
            int pxFlag = Arrays.stream(command).toList().indexOf("px");
            if(command.length == 3){
                LocalDateTime expiry = LocalDateTime.of(LocalDate.MAX, LocalTime.MAX);
                Value val = new Value(command[2],LocalDateTime.now(),expiry);
                map.put(command[1],val);
            }
            else if(pxFlag> -1){
                int delta = Integer.parseInt(command[pxFlag+1]);
                LocalDateTime now = LocalDateTime.now();
                Value val = new Value(command[2],LocalDateTime.now(),now.plus(delta, ChronoUnit.MILLIS));
                map.put(command[1],val);
            }
            return "+OK\r\n";
        }catch(Exception e){
            System.out.println(e.getMessage());
            return "$-1\r\n";
        }
    }
    public String Get(String[] command, LocalDateTime curr){
        try{
            Value val = map.get(command[1]);
            if(curr.isBefore(val.expiry) || curr.isEqual(val.expiry)){
                return "+"+ val.val +"\r\n";
            }else{
                map.remove(command[1]);
                return "$-1\r\n";
            }
        }catch(Exception e){
            System.out.println(e.getMessage());
            return "$-1\r\n";
        }
    }
}

