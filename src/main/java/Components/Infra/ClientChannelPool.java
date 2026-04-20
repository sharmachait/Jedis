package Components.Infra;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.stereotype.Component;

@Component
public class ClientChannelPool {
    ConcurrentHashMap<String, Channel> channelByChannelId;
    ConcurrentHashMap<Integer, Set<String>> channelIdsByClientIds;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    public ClientChannelPool(){
        this.channelByChannelId = new ConcurrentHashMap<>();
        this.channelIdsByClientIds = new ConcurrentHashMap<>();
    }
    
    public int subscribe(Client client, String channelId){
        rwLock.writeLock().lock();
        try{
            if(!channelByChannelId.containsKey(channelId)){
                Channel newChannel = new Channel(channelId);
                channelByChannelId.put(channelId, newChannel);
            }
            if(!channelIdsByClientIds.containsKey(client.id)){
                channelIdsByClientIds.put(client.id, new HashSet<>());
            }

            Channel channel = channelByChannelId.get(channelId);

            channelIdsByClientIds.get(client.id).add(channelId);
            channel.clients.add(client);

            return channelIdsByClientIds.get(client.id).size();
        } finally {
              rwLock.writeLock().unlock();
        }
    }
    public boolean isClientSubscribed(Client client){
        rwLock.readLock().lock();
        try{
            Set<String> channelIds = channelIdsByClientIds.getOrDefault(client.id, new HashSet<>());
            return channelIds.size() > 0;
        } finally {
            rwLock.readLock().unlock();    
        }
    }
    public int publish(String channelId, String message){
         Set<Client> snashot;
         rwLock.readLock().lock();
         try{
             Channel channel = channelByChannelId.get(channelId);
             if(channel == null) return 0;
             snashot = new HashSet<>(channel.clients);
         } finally {
             rwLock.readLock().unlock();
         }
         int res = 0;
         try{
             for(Client client: snashot){
                 client.send(message);
                 res ++;
             }
         } catch (Exception e){
             System.err.println("sent to " + res + " but needed to send to "+ snashot.size());
             return res;
         }

         return snashot.size();
    }
}
