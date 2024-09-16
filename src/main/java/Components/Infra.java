package Components;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class Infra {
    public int bytesSentToSlave = 0;
    public int times=0;
    public ConcurrentLinkedQueue<Slave> slaves =
            new ConcurrentLinkedQueue<>();

    public ConcurrentLinkedQueue<Client> clients =
            new ConcurrentLinkedQueue<>();

    public int slavesThatAreCaughtUp = 0;

    public void slaveAck(int ackResponse){

        if (this.bytesSentToSlave == ackResponse){
            this.slavesThatAreCaughtUp++;
        }
    }
}

