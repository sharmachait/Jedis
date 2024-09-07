import java.util.UUID;

public class RedisConfig {
    public String role;
    public int port;
    public int masterPort;
    public String masterHost;
    public String masterReplId;
    public long masterReplOffset;
    public String dir;
    public String dbfilename;

    public RedisConfig(String role, int port, int masterPort, String masterHost ) {
        this.role=role;
        this.port=port;
        this.masterHost=masterHost;
        this.masterPort=masterPort;
        masterReplId=UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        masterReplOffset = 0;
        dir=null;
        dbfilename=null;
    }
    public RedisConfig(int port) {
        this.role="master";
        this.port=port;
        this.masterHost=".";
        this.masterPort=Integer.MIN_VALUE;
        masterReplId=UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        masterReplOffset = 0;
        dir=null;
        dbfilename=null;
    }
    public RedisConfig() {
        this.role="master";
        this.port=6379;
        this.masterHost=".";
        this.masterPort=Integer.MIN_VALUE;
        masterReplId=UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        masterReplOffset = 0;
        dir=null;
        dbfilename=null;
    }
}
