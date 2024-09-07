import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Store {
    private Map<String,Value> map;
    public Store() {
        map = new HashMap<String,Value>();
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
}

class Value{
    public String val;
    public LocalDateTime created;
    public LocalDateTime expiry;
    public Value(String val, LocalDateTime created, LocalDateTime expiry) {
        this.val = val;
        this.created = created;
        this.expiry = expiry;
    }
}
