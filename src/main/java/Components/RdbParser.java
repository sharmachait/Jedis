package Components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class RdbParser {
    private static final Charset ASCII = StandardCharsets.US_ASCII;
    private static final int EOF = 0xFF;
    private static final int SELECTDB = 0xFE;
    private static final int EXPIRETIME = 0xFD;
    private static final int EXPIRETIMEMS = 0xFC;
    private static final int RESIZEDB = 0xFB;
    private static final int AUX = 0xFA;
    private final RedisConfig redisConfig;
    @Autowired
    public RdbParser(RedisConfig redisConfig) {
        this.redisConfig = redisConfig;
    }

    public List<KeyValuePair> parse(DataInputStream data) throws IOException {
        parseHeader(data);
        KeyValuePair next = new KeyValuePair();
        List<KeyValuePair> pairs = new ArrayList<>();
        System.out.println("post header ------------------------------------ post header");
        while(data.available() > 0) {
            System.out.println(data.readByte());
        }
//        while(data.available()>0){
//            int type = data.readByte();
//            if(type == EOF)
//                    break;
//            switch(type){
//                case EOF:
//                    break;
//            }
//
//        }
        return pairs;
    }

    private void parseHeader(DataInputStream data) throws IOException {
        byte[] header = new byte[9];
        data.readFully(header);
        String headerStr = new String(header, ASCII);
        redisConfig.header = headerStr;
        String redis = headerStr.substring(0, 5);
        int version = Integer.parseInt(headerStr.substring(5));
        if (!redis.equals("REDIS")) {
            throw new IOException("Invalid RDB file header");
        }
        if (version < 1) {
            throw new IOException("Unknown version");
        }
    }
}
