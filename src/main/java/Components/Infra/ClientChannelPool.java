package Components.Infra;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ClientChannelPool {
    Map<String, Channel> channelByChannelId;
    Map<Integer, Set<String>> channelIdsByClientIds;
    Map<String, Set<Integer>> clientIdsByChannelIds;

    public ClientChannelPool(){
        this.channelByChannelId = new HashMap<>();
        this.channelIdsByClientIds = new HashMap<>();
        this.clientIdsByChannelIds = new HashMap<>();
    }
    
    public int subscribe(Client client, String channelId){
        if(!channelByChannelId.containsKey(channelId)){
            Channel newChannel = new Channel(channelId);
            channelByChannelId.put(channelId, newChannel);
            clientIdsByChannelIds.put(channelId, new HashSet<>());
        }

        if(!channelIdsByClientIds.containsKey(client.id)){
            channelIdsByClientIds.put(client.id, new HashSet<>());
        }
        
        Channel channel = channelByChannelId.get(channelId);

        channelIdsByClientIds.get(client.id).add(channelId);
        clientIdsByChannelIds.get(channelId).add(client.id);

        return channelIdsByClientIds.get(client.id).size();
    }
    public boolean isClientSubscribed(Client client){
        Set<String> channelIds = channelIdsByClientIds.getOrDefault(client.id, new HashSet<>());
        return channelIds.size() > 0;
    }
}
