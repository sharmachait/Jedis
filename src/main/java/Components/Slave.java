package Components;

import java.util.ArrayList;
import java.util.List;

public class Slave {
    public List<String> capcabilities;
    public Client connection;
    public int id;
    public Slave(int id, Client client){
        this.id = id;
        this.capcabilities = new ArrayList<>();
        connection = client;
    }
}
