package Components.Repository;

import Components.Infra.Client;
import Components.Service.RespSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.tokens.ValueToken;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

@Component
public class Store {
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    public ConcurrentHashMap<String, Value> map;
    public ConcurrentHashMap<String, Set<Integer>> watchingClientsListForKeys;
    public Set<Integer> failTransactionFor;
    @Autowired
    public RespSerializer respSerializer;
    public Store(){
        map = new ConcurrentHashMap<>();
        watchingClientsListForKeys = new ConcurrentHashMap<>();
        failTransactionFor = ConcurrentHashMap.newKeySet();
    }
    public boolean isWatchedkey(String key){
      return watchingClientsListForKeys.containsKey(key);    
    }
    public void addToFailTransactionForExcept(String key, Client currentClient){
        Set<Integer> watchers = watchingClientsListForKeys.getOrDefault(key, new HashSet<>());
        for(int id: watchers){
            if(id != currentClient.id) {
              this.failTransactionFor.add(id);
            }
        }
    }

    public void addToFailTransactionFor(String key){
        Set<Integer> watchers = watchingClientsListForKeys.getOrDefault(key, new HashSet<>());
        for(int id: watchers){
            this.failTransactionFor.add(id);
        }
    }
    public Set<String> getKeys(){
        rwLock.readLock().lock();
        try{
            return map.keySet();
        }finally{
            rwLock.readLock().unlock();
        }
    }

    public String blpop(String key) throws InterruptedException{
        rwLock.writeLock().lock();
        LinkedBlockingDeque<String> deque;
        try{
            Value value = map.get(key);
            if(value == null) {
                value = new Value(ValueType.LIST, LocalDateTime.now(), LocalDateTime.MAX);
                map.put(key, value);
            }
            deque = value.list;
        } finally{
            rwLock.writeLock().unlock();
        }
        return deque.takeFirst();
    }
    public String blpop_timeout(String key, long timeoutMs) throws InterruptedException {
        rwLock.writeLock().lock();
        LinkedBlockingDeque<String> deque;
        try{
            Value value = map.get(key);
            if(value == null) {
                value = new Value(ValueType.LIST, LocalDateTime.now(), LocalDateTime.MAX);
                map.put(key, value);
            }
            deque = value.list;
        } finally{
            rwLock.writeLock().unlock();
        }
        return deque.pollFirst(timeoutMs, TimeUnit.MILLISECONDS);
    }
    public boolean addWatcherForKey(String key, Client watcher){
        rwLock.writeLock().lock();
        try{
            Set<Integer> watchers = watchingClientsListForKeys.getOrDefault(key, null);
            if (watchers == null){
                watchers = new HashSet<>();
            }
            watchers.add(watcher.id);
            watchingClientsListForKeys.put(key, watchers);
            return true;
        } catch(Exception e){
            return false;
        } finally {
          rwLock.writeLock().unlock();
        }
    }

    public boolean removeWatcherForKey(String key, Client watcher) {
        rwLock.writeLock().lock();
        try{
            Set<Integer> watchers = watchingClientsListForKeys.getOrDefault(key, null);
            if (watchers == null){
                return true;
            }
            watchers.remove(watcher.id);
            if(watchers.isEmpty()){
              watchingClientsListForKeys.remove(key);
            }else{
              watchingClientsListForKeys.put(key, watchers);
            }
            return true;
        } catch(Exception e){
            return false;
        } finally {
          rwLock.writeLock().unlock();
        }
    }
    public String lpop(String key){
        rwLock.writeLock().lock();
        try{
            Value value = map.get(key);
            if(value == null) return "";
            return value.list.removeFirst();
        }finally{
            rwLock.writeLock().unlock();
        }
    }
    public int llen(String key){
        rwLock.readLock().lock();
        try{
            Value value = map.get(key);
            if(value == null) return 0;
            return value.list.size();
        }finally{
            rwLock.readLock().unlock();
        }
    }
    public int lpush(String key, String val){
       rwLock.writeLock().lock();
       try{
           Value value = map.get(key);
           if(value == null) {
               value = new Value(ValueType.LIST, LocalDateTime.now(), LocalDateTime.MAX);
               map.put(key, value);
           }  else if(value.type != ValueType.LIST){
               return -1;
           }
           value.list.addFirst(val);
           return value.list.size();
       }finally{
           rwLock.writeLock().unlock();
       }
    }
    public int rpush(String key, String val){
       rwLock.writeLock().lock();
       try{
           Value value = map.get(key);
           if(value == null) {
               value = new Value(ValueType.LIST, LocalDateTime.now(), LocalDateTime.MAX);
               map.put(key, value);
           }  else if(value.type != ValueType.LIST){
               return -1;
           }
           value.list.add(val);
           return value.list.size();
       }finally{
           rwLock.writeLock().unlock();
       }
    }
    public String[] lrange(String key, int start, int stop) {
        rwLock.readLock().lock();
        try{
            Value value = map.get(key);
            if(value == null) {
                return new String[0];
            } else if(value.type!=ValueType.LIST){
                throw new RuntimeException("-ERR WRONGTYPE Operation against a key holding the wrong kind of value\r\n");
            }
            int length = value.list.size();

            int startPositive = start < 0? start + length :start;
            int stopPositive = stop < 0? stop + length: stop;
            if(startPositive > stopPositive) return new String[0];
            int stopExclusive = stopPositive+1;
            if(startPositive >= length) return new String[0];
            stopExclusive = Math.min(stopExclusive, length);
            startPositive = Math.max(startPositive, 0);
            List<String> snapshot = new ArrayList<>(value.list);
            return snapshot.subList(startPositive, stopExclusive).toArray(new String[0]);
        }finally{
            rwLock.readLock().unlock();
        }
    }
    public String set(String key, String val){
        rwLock.writeLock().lock();
        try{
            Value value = new Value(val, LocalDateTime.now(), LocalDateTime.MAX);
            map.put(key, value);
            return "+OK\r\n";
        } catch (Exception e) {
            return "$-1\r\n";
        }finally{
            rwLock.writeLock().unlock();
        }
    }

    public String set(String key, String val, int expiryMilliseconds){
        rwLock.writeLock().lock();
        try{
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime exp = now.plus(expiryMilliseconds, ChronoUnit.MILLIS);
            Value value = new Value(val, now, exp);
            map.put(key, value);
            return "+OK\r\n";
        } catch (Exception e) {
            return "$-1\r\n";
        }finally{
            rwLock.writeLock().unlock();
        }
    }

    public String get(String key){
        rwLock.readLock().lock();
        try{
            LocalDateTime now = LocalDateTime.now();
            Value value = map.get(key);

            if(value!=null && value.expiry.isBefore(now)){
                map.remove(key);
                return "$-1\r\n";
            }
            return respSerializer.serializeBulkString(value.val);
        } catch (Exception e) {
            return "$-1\r\n";
        }finally{
            rwLock.readLock().unlock();
        }
    }

    public Value getValue(String key) {
        rwLock.readLock().lock();
        try{
            LocalDateTime now = LocalDateTime.now();
            Value value = map.getOrDefault(key, null);

            if(value!=null && value.expiry.isBefore(now)){
                map.remove(key);
                return null;
            }
            return value;
        } catch (Exception e) {
            return null;
        }finally{
            rwLock.readLock().unlock();
        }
    }

    public void executeTransaction(
            Client client,
            BiFunction<String[], Map<String, Value>, String> transactionCacheApplier
    ){
        rwLock.writeLock().lock();
        Map<String, Value> localCache = new HashMap<>();
        List<String> responses = new ArrayList<>();
        try{
            while(!client.commandQueue.isEmpty()){
                String[] command = client.commandQueue.poll();
                String response = transactionCacheApplier.apply(command, localCache);
                responses.add(response);
            }

            //control will only come here when the queue is empty, that means no other commands in the transaction left to be applied
            for(Map.Entry<String, Value> entry : localCache.entrySet()){
                String key = entry.getKey();
                Value value = entry.getValue();

                if(value.isDeletedInTransaction){
                    this.map.remove(key);
                }else{
                    this.map.put(key, value);
                }
            }
            if(client.watchSet!=null){
                for(String key: client.watchSet) {
                    removeWatcherForKey(key, client);
                }
            }
            client.transactionResponse.addAll(responses);
        }finally {
            rwLock.writeLock().unlock();
        }
    }
    public Value xadd(String key, String entryId, Map<String, String> entries){
        rwLock.writeLock().lock();
        Value val = Value.newStream();
        try{
            val.stream.put(entryId, entries);
            map.put(key, val);
        }finally{
            rwLock.writeLock().unlock();
        }
        return val;
    }
}
