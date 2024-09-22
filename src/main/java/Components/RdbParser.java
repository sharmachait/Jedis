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
    private int readByte(DataInputStream data) throws IOException {
        int z = data.readByte();
        return z & 0xff;
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
    public List<KeyValuePair> parse(DataInputStream data) throws IOException {
        parseHeader(data);
        KeyValuePair next = new KeyValuePair();
        List<KeyValuePair> pairs = new ArrayList<>();
        System.out.println("post header ------------------------------------ post header");
        System.out.println("EOF "+EOF);
        System.out.println("SELECTDB "+SELECTDB);
        System.out.println("EXPIRETIME "+EXPIRETIME);
        System.out.println("EXPIRETIMEMS "+EXPIRETIMEMS);
        System.out.println("RESIZEDB "+RESIZEDB);
        System.out.println("AUX "+AUX);

        while(data.available()>0){
            int type = readByte(data);
            System.out.println("type to process ===================================="+type);
            System.out.println(type);
//            if(type == EOF)
//                    break;
//            switch(type){
//                case AUX:
//                    readAux(data);
//                    break;
//                case EOF:
//                    break;
//            }
        }
        return pairs;
    }


//    private void readAux(DataInputStream data) throws IOException {
//        String key = parseStringEncdoded(data);
//        String value = parseStringEncdoded(data);
//
//        String res = "Auxiliary: { key: " + key + ", value: " + value + " }";
//        System.out.println(res);
//    }
//    private String parseStringEncdoded(DataInputStream data) throws IOException {
//        int firstByte = readByte(data);
//        int type = (firstByte >> 6) & 0x03;
//        int len;
//        byte[] data;
//
//        switch (type) {
//            case RDB_6BIT_ENC:
//                len = firstByte & 0x3f;
//                data = new byte[len];
//                inputStream.readFully(data);
//                return new String(data, ASCII);
//            case RDB_14BIT_ENC:
//                len = ((firstByte & 0x3f) << 8) | readByte();
//                data = readBytes(len);
//                return new String(data, ASCII);
//            case RDB_32BIT_ENC:
//                len = readInt();
//                data = readBytes(len);
//                return new String(data, ASCII);
//            case RDB_ENVVAL:
//                return parseSpecialStringEncoded(firstByte & 0x3f);
//            default:
//                return null;
//    }
}
