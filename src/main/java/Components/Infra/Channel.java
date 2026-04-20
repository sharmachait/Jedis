package Components.Infra;

import java.util.LinkedList;
import java.util.Queue;

public class Channel {
    public String id;
    public Queue<String> events;
    public Channel(String id){
        this.id = id;
        this.events = new LinkedList<>();
    }
}
