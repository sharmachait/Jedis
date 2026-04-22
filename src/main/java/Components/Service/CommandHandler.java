package Components.Service;

import Components.Infra.Client;
import Components.Infra.ClientChannelPool;
import Components.Infra.ConnectionPool;
import Components.Infra.Slave;
import Components.Repository.OptimisticLockException;
import Components.Repository.Store;
import Components.Repository.Value;
import Components.Server.RedisConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.logging.Level;

@Component
public class CommandHandler {
    private static final String emptyRdbFile = "UkVESVMwMDEx+glyZWRpcy12ZXIFNy4yLjD6CnJlZGlzLWJpdHPAQPoFY3RpbWXCbQi8ZfoIdXNlZC1tZW3CsMQQAPoIYW9mLWJhc2XAAP/wbjv+wP9aog==";
    @Autowired
    public RespSerializer respSerializer;
    @Autowired
    public Store store;
    @Autowired
    public RedisConfig redisConfig;
    @Autowired
    public ConnectionPool connectionPool;
    @Autowired
    public ClientChannelPool clientChannelPool;
    public String ping(String[] command){
        return "+PONG\r\n";
    }
    public String watch(String[] command, Client client){
        for(int i=1; i< command.length; i++){
            client.addToWatchSet(command[i]);
            store.addWatcherForKey(command[i], client);
        }
        return "+OK\r\n";
    }
    public String echo(String[] command){
        return respSerializer.serializeBulkString(command[1]);
    }
    public String rpush(String[] command){
        String key = command[1];
        String val = command[2];
        int length = store.rpush(key, val);

        return respSerializer.respInteger(length);
    }
    public String set(String[] command){
        // TODO global exception handling
        try{
            String key = command[1];
            String value = command[2];

            if(this.store.isWatchedkey(key)){
                this.store.addToFailTransactionFor(key);
            }

            int pxFlag = Arrays.stream(command).toList().indexOf("px");
            // -1
            if(pxFlag == -1) {
              pxFlag = Arrays.stream(command).toList().indexOf("PX");
            }
            if(pxFlag > -1){
                int delta = Integer.parseInt( command[ pxFlag + 1 ] );
                return store.set(key, value, delta);
            }else{
                return store.set(key, value);
            }
        }catch (Exception e){
            return "$-1\r\n";
        }
    }
    public String get(String[] command){
        try{
            String key = command[1];
            return store.get(key);
        } catch (Exception e) {
            return "$-1\r\n";
        }
    }
    public String info(String[] command){
        // command[0]; info
        int replication = Arrays.stream(command).toList().indexOf("replication");
        if(replication > -1){
            String role = "role:"+redisConfig.getRole();
            String masterReplId = "master_replid:"+redisConfig.getMasterReplId();
            String masterReplOffset = "master_repl_offset:"+redisConfig.getMasterReplOffset();

            String []info = new String[]{role, masterReplId, masterReplOffset};

            String replicationData = String.join("\r\n", info);

            return respSerializer.serializeBulkString(replicationData);
        }
        return "";
    }
    public String replconf(String[] command, Client client) {

        switch (command[1]){
            case "GETACK":
                String [] replconfAck = new String[]{"REPLCONF", "ACK", redisConfig.getMasterReplOffset()+""};
                return respSerializer.respArray(replconfAck);
            case "ACK":
                int ackResponse = Integer.parseInt(command[2]);
                connectionPool.slaveAck(ackResponse);
//                break;
                return "";
            case "listening-port":
                connectionPool.removeClient(client);
                Slave s = new Slave(client);
                connectionPool.addSlave(s);
                return "+OK\r\n";

            case "capa":
                Slave slave = null;
                for(Slave ss: connectionPool.getSlaves()){
                    if(ss.connection.equals(client)){
                        slave = ss;
                        break;
                    }
                }
                for(int i=0; i<command.length; i++){
                    if(command[i].equals("capa")){
                        slave.capabilities.add(command[i+1]);
                    }
                }
                return "+OK\r\n";
        }

        return "+OK\r\n";
    }
    public byte[] concatenate(byte[] a, byte[] b){
        byte[] result = new byte[a.length +b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
    public ResponseDto psync(String[] command) {
        String replicationIdMaster = command[1];
        String replicationOffSetMaster = command[2];

        if(replicationIdMaster.equals("?") && replicationOffSetMaster.equals("-1")){
            String replicationId = redisConfig.getMasterReplId();
            long replicationOffset = redisConfig.getMasterReplOffset();
            String res = "+FULLRESYNC "+ replicationId +" "+replicationOffset+"\r\n";

            byte[] rdbFileData = Base64.getDecoder().decode(emptyRdbFile);

            String length = rdbFileData.length+"";

            String fullResyncHeader = "$"+ length +"\r\n";
            byte[] header = fullResyncHeader.getBytes();

            connectionPool.slavesThatAreCaughtUp++;

            return new ResponseDto(res, concatenate(header, rdbFileData));
        }else{
            return new ResponseDto("Options not supported yet.");
        }

    }
    public String publish(String[] command){
        printCommand(command);
        String channelId = command[1];
        String message = command[2];
        int res = clientChannelPool.publish(channelId, message);
        return respSerializer.respInteger(res);
    }
    public String subscribe(String[] command, Client client) {
        String channelId = command[1];
        int numberOfChannels = clientChannelPool.subscribe(client, channelId);
        String numberOfChannelsResp = respSerializer.respInteger(numberOfChannels);
        String response = "*3\r\n$9\r\nsubscribe\r\n"
                           + respSerializer.serializeBulkString(channelId) +
                           numberOfChannelsResp;
        client.isSubscribed = true;
        return response;
    }
    public String unsubscribe(String[] command, Client client) {
        String channelId = command[1];
        int numberOfChannels = clientChannelPool.unsubscribe(client, channelId);
        String numberOfChannelsResp = respSerializer.respInteger(numberOfChannels);
        String response = "*3\r\n$11\r\nunsubscribe\r\n"
                           + respSerializer.serializeBulkString(channelId) +
                           numberOfChannelsResp;
        client.isSubscribed = false;
        return response;
    }
    public String wait(String[] command, Instant start) {
        String[] getackarr = new String[] { "REPLCONF", "GETACK", "*" };
        String getack = respSerializer.respArray(getackarr);
        byte[] bytearr = getack.getBytes();
        int bufferSize = bytearr.length;

        int required = Integer.parseInt(command[1]);
        int time = Integer.parseInt(command[2]);

        for(Slave slave: connectionPool.getSlaves()){
            CompletableFuture.runAsync(()->{
                try {

                    slave.connection.send(getack.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });
        }

        int res = 0;
        while(true){
            if(res>=required)
                break;
            if(Duration.between(start, Instant.now()).toMillis() >= time)
                break;
            res= connectionPool.slavesThatAreCaughtUp;
        }
        connectionPool.bytesSentToSlaves+=bufferSize;
        if(res > required)
            return respSerializer.respInteger(required);
        return respSerializer.respInteger(res);
    }
    public String incr(String[] command) {
        String key =command[1];
        String res = "";
        try{
            if(this.store.isWatchedkey(key)){
                this.store.addToFailTransactionFor(key);
            }

            Value value = store.getValue(key);
            if(value == null){
                store.set(key, "0");
                value = store.getValue(key);
            }

            int val = Integer.parseInt(value.val);
            val++;
            value.val = val+"";

            res = respSerializer.respInteger(val);

        } catch (Exception e) {
            res = "-ERR value is not an integer or out of range\r\n";
        }
        return res;
    }
    private void printCommand(String[] command){
      for(String s: command){
        System.out.print(s+" ");
      }
    }


    public BiFunction<String[], Map<String, Value>, String> getTransactionCommandCacheApplier(Client client){
        final Store localStore = this.store;
        final RespSerializer localSerializer = this.respSerializer;
        return (String[] command, Map<String, Value> map)->{
            isOptimisticLockViolated(client);                    
            String res = "";
            switch (command[0]) {
                case "SET":
                    if(this.store.isWatchedkey(command[1])){
                        this.store.addToFailTransactionForExcept(command[1], client);
                    }
                    res = handleSetCommandTransactional(command, map, localSerializer, localStore);
                    break;
                case "GET":
                    res = handleGetCommandTransactional(command, map, localSerializer, localStore);
                    break;
                case "INCR":
                    if(this.store.isWatchedkey(command[1])){
                        this.store.addToFailTransactionForExcept(command[1], client);
                    }
                    res = handleIncrCommandTransactional(command, map, localSerializer, localStore);
                    break;
                case "DEL":
                    if(this.store.isWatchedkey(command[1])){
                        this.store.addToFailTransactionForExcept(command[1], client);
                    }
                    res = handleDelCommandTransactional(command, map, localSerializer, localStore);
                    break;
                default:
                    res = "-ERR unknown command '"+command[0]+"'\r\n";
                    break;
            }
            return res;
        };
    }

    private void isOptimisticLockViolated(Client client){
        if(this.store.failTransactionFor.contains(client.id)){
          this.store.failTransactionFor.remove(client.id);
          client.endTransaction();
          throw new OptimisticLockException();
        } 
    }

    private String handleDelCommandTransactional(String[] command, Map<String, Value> map, RespSerializer localSerializer, Store localStore) {
        String key = command[1];
        Value valueToUse;
        Value cachedValue = map.getOrDefault(key, null);
        if(cachedValue==null){
            Value storeValue = localStore.getValue(key);
            if(storeValue == null){
                //both are null
                return "-ERR deleting and invalid key\r\n";
            }else{
                valueToUse = new Value(storeValue.val, storeValue.created, storeValue.expiry);
            }
        }else{
            valueToUse = cachedValue;
        }
        valueToUse.isDeletedInTransaction =true;
        map.put(key, valueToUse);
        return "+OK\r\n";
    }

    private String handleIncrCommandTransactional(
            String[] command,
            Map<String, Value> map,
            RespSerializer localSerializer,
            Store localStore) {
        try{
            String key = command[1];
            Value valueToUse;
            Value cachedValue = map.getOrDefault(key, null);
            if(cachedValue==null){
                Value storeValue = localStore.getValue(key);
                if(storeValue==null) {
                    valueToUse = new Value("0", LocalDateTime.now(), LocalDateTime.MAX);
                }else{
                    valueToUse = new Value(storeValue.val, storeValue.created, storeValue.expiry);
                }
            }else{
                valueToUse=cachedValue;
            }
            int val = Integer.parseInt(valueToUse.val);
            val++;
            valueToUse.val = String.valueOf(val);
            map.put(key, valueToUse);
            return respSerializer.respInteger(val);
        } catch (Exception e) {
            return "-ERR value is not an integer or out of range\r\n";
        }
    }
    private String handleGetCommandTransactional(
            String[] command,
            Map<String, Value> map,
            RespSerializer localSerializer,
            Store localStore) {
        String key = command[1];
        Value valueToUse;
        Value cachedValue = map.getOrDefault(key, null);
        if(cachedValue==null){
            Value storeValue = localStore.getValue(key);
            if(storeValue ==null){
                //both are null
                return localStore.get(key);
            }else{
                valueToUse = new Value(storeValue.val, storeValue.created, storeValue.expiry);
                map.put(key, valueToUse);
            }
        }else{
            valueToUse = cachedValue;
        }
        return respSerializer.serializeBulkString(valueToUse.val);
    }
    private String handleSetCommandTransactional(
            String[] command,
            Map<String, Value> map,
            RespSerializer localSerializer,
            Store localStore) {
        String key = command[1];
        Value newValue = new Value(command[2], LocalDateTime.now(), LocalDateTime.MAX);
        map.put(key, newValue);
        return "+OK\r\n";
    }
}
