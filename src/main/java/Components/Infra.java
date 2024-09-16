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
        System.out.println();
        System.out.println("--------------------------------- times this function is called");
        this.times++;
        System.out.println(this.times);
        System.out.println();
        System.out.println();
        System.out.println("--------------------------------------------------- prev value");
        System.out.println(this.slavesThatAreCaughtUp);
        System.out.println();
        System.out.println("--------------------------------------------------- ack response");
        System.out.println(ackResponse);
        System.out.println();
        if (this.bytesSentToSlave == ackResponse){
            this.slavesThatAreCaughtUp++;
        }
    }
}

