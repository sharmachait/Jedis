package Components.Infra;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class Channel {
    public String id;
    public Queue<String> events;
    public Set<Client> clients;
    public Channel(String id){
        this.id = id;
        this.events = new LinkedList<>();
        this.clients = new HashSet<>();
    }
}
