package Components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final int RDB_6BIT_ENC = 0;
    private static final int RDB_14BIT_ENC = 1;
    private static final int RDB_32BIT_ENC = 0x80;
    private static final int RDB_64BIT_ENC = 0x81;
    private static final int RDB_ENVVAL = 3;

    private static final int RDB_STR_8BIT_ENC = 0;
    private static final int RDB_STR_16BIT_ENC = 1;
    private static final int RDB_STR_32BIT_ENC = 2;
    private static final int RDB_STR_LZF_COMP = 3;

    private static final int RDB_ENC_BIT8 = 0;
    private static final int RDB_ENC_BIT16 = 1;
    private static final int RDB_ENC_BIT32 = 2;
    private final RedisConfig redisConfig;
    @Autowired
    public RdbParser(RedisConfig redisConfig) {
        this.redisConfig = redisConfig;
    }
    private int readByte(DataInputStream data) throws IOException {
        int z = data.readByte();
        return z & 0xff;
    }
    private byte[] readBytes(int numOfBytes, DataInputStream data) throws IOException {
        byte[] b = new byte[numOfBytes];
        data.readFully(b);
        return b;
    }
    private int readLength(DataInputStream data) throws IOException {
        int firstByte = readByte(data);
        int type = (firstByte >> 6) & 0x03;

        if (type == RDB_ENVVAL) {
            return firstByte & 0x3f;
        } else if (type == RDB_6BIT_ENC) {
            return firstByte & 0x3f;
        } else if (type == RDB_14BIT_ENC) {
            return ((firstByte & 0x3f) << 8) | readByte(data);
        } else if (firstByte == RDB_32BIT_ENC) {
            return data.readInt();
        } else if (firstByte == RDB_64BIT_ENC) {
            byte[] b = readBytes(8,data);
            return ByteBuffer.wrap(b).getInt();
        } else {
            throw new IOException("Unknown encoding type");
        }
    }
    private int readSignedByte(DataInputStream data) throws IOException {
        return data.readByte();
    }
    private int readShort(DataInputStream data) throws IOException {
        return data.readShort();
    }
    private int readInt(DataInputStream data) throws IOException {
        return data.readInt();
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
            System.out.println("type to process ====================================");
            System.out.println(type);
            if(type == EOF)
                    break;
            switch(type){
                case SELECTDB:
                    readSelectDB(data);
                    break;
                case RESIZEDB:
                    readResizeDb(data);
                    break;
                case AUX:
                    readAux(data);
                    break;
                case EXPIRETIME:
                    readExpiryTime(data, next);
                    continue;
                case EXPIRETIMEMS:
                    readExpiryTimeMilli(data, next);
                    continue;
                default:
                    readEntry(type,data,next);
                    pairs.add(next);
                    next = new KeyValuePair();
                    break;
            }
        }
        return pairs;
    }
    private void readSelectDB(DataInputStream data) throws IOException {
        int id = readLength(data);
        String res = "SELECT DB (" + id + ")";
        System.out.println(res);
    }
    private void readExpiryTime(DataInputStream data, KeyValuePair next) throws IOException {
        byte[] b = readBytes(4, data);
        long expiry = ((long) b[3] & 0xff) << 24 | ((long) b[2] & 0xff) << 16 | ((long) b[1] & 0xff) << 8 | ((long) b[0] & 0xff);
        Timestamp expT = new Timestamp(expiry * 1000);
        System.out.println("Expiry ==> Second " + expiry + " =>> " + expT);
        next.setExpiryTime(expT);
    }
    private void readResizeDb(DataInputStream data) throws IOException {
        int databaseHashSize = readLength(data);
        int expiryHashSize = readLength(data);

        String res = "Resize DB {" + databaseHashSize + ", " + expiryHashSize + "}";
        System.out.println(res);
    }
    private void readExpiryTimeMilli(DataInputStream data, KeyValuePair next) throws IOException {
        byte[] b = readBytes(8, data);
        long expiry = ((long) b[7] & 0xff) << 56 // reverse as it's in little endian format
                | ((long) b[6] & 0xff) << 48
                | ((long) b[5] & 0xff) << 40
                | ((long) b[4] & 0xff) << 32
                | ((long) b[3] & 0xff) << 24
                | ((long) b[2] & 0xff) << 16
                | ((long) b[1] & 0xff) << 8
                | ((long) b[0] & 0xff);
        Timestamp expT = new Timestamp(expiry);
        System.out.println("Expiry Milli => " + expiry + " => " + expT);
        next.setExpiryTime(expT);
    }
    private void readEntry(int type, DataInputStream data, KeyValuePair next) throws IOException {
        switch (type) {
            case 0:
                readString(data,next);
                break;
            case 1:
                readList(data,next);
                break;
            case 2:
                readSet(data,next);
                break;
            case 3:
                readSortedSet(data,next);
                break;
            case 4:
                readHash(data,next);
                break;
            default:
                throw new UnsupportedOperationException("Unknown value type: " + type);
        }
    }
    private void readString(DataInputStream data, KeyValuePair next) throws IOException {
        String key = parseStringEncdoded(data);
        String value = parseStringEncdoded(data);
        System.out.println("{ key: " + key + ", value: " + value + " }");

        next.setKey(key);
        next.setValue(value);
        next.setType(ValueType.STRING);
    }
    private void readList(DataInputStream data, KeyValuePair next) throws IOException {
        String key = parseStringEncdoded(data);
        int size = readLength(data);

        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(parseStringEncdoded(data));
        }

        next.setKey(key);
        next.setValue(list);
        next.setType(ValueType.LIST);
    }
    private void readSet(DataInputStream data, KeyValuePair next) throws IOException {
        String key = parseStringEncdoded(data);
        int size = readLength(data);

        List<String> set = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            set.add(parseStringEncdoded(data));
        }

        next.setKey(key);
        next.setValue(set);
        next.setType(ValueType.SET);
    }
    private void readSortedSet(DataInputStream data, KeyValuePair next) throws IOException {
        String key = parseStringEncdoded(data);
        int size = readLength(data);

        List<String> valueScorePairs = new ArrayList<>(size * 2);
        for (int i = 0; i < size; i++) {
            valueScorePairs.add(parseStringEncdoded(data));
            valueScorePairs.add(parseDoubleScoreString(data));
        }

        next.setKey(key);
        next.setValue(valueScorePairs);
        next.setType(ValueType.SORTED_SET);
    }
    private String parseDoubleScoreString(DataInputStream data) throws IOException {
        int len = readByte(data);

        return switch (len) {
            case 0xff -> String.valueOf(Double.NEGATIVE_INFINITY);
            case 0xfe -> String.valueOf(Double.POSITIVE_INFINITY);
            case 0xfd -> String.valueOf(Double.NaN);
            default -> {
                byte[] buff = readBytes(len, data);
                yield new String(buff, ASCII);
            }
        };
    }
    private void readHash(DataInputStream data, KeyValuePair next) throws IOException {
        String key = parseStringEncdoded(data);
        System.out.println("HASH key => " + key);
        int size = readLength(data);
        System.out.println("Hash size => " + size);

        HashMap<String, String> hash = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String mapKey = parseStringEncdoded(data);
            String mapValue = parseStringEncdoded(data);
            System.out.println("{ key: " + mapKey + ", value: " + mapValue + " }");
            hash.put(mapKey, mapValue);
        }

        next.setKey(key);
        next.setValue(hash);
        next.setType(ValueType.HASH);
    }
    private void readAux(DataInputStream data) throws IOException {
        String key = parseStringEncdoded(data);
        String value = parseStringEncdoded(data);

        String res = "Auxiliary: { key: " + key + ", value: " + value + " }";
        System.out.println(res);
    }
    private String parseStringEncdoded(DataInputStream data) throws IOException {
        int firstByte = readByte(data);
        int type = (firstByte >> 6) & 0x03;// extracts the 7th and 8th bits
        int len;
        byte[] buffer;

        switch (type) {
            case RDB_6BIT_ENC:
                len = firstByte & 0x3f;// extracts the lower 6 bits
                buffer = new byte[len];
                data.readFully(buffer);
                return new String(buffer, ASCII);
            case RDB_14BIT_ENC:
                len = ((firstByte & 0x3f) << 8) | readByte(data);// gives 16 bit value first 6 bits from the first byte the last 8 from the data
                buffer = readBytes(len, data);
                return new String(buffer, ASCII);
            case RDB_32BIT_ENC:
                len = readInt(data);
                buffer = readBytes(len, data);
                return new String(buffer, ASCII);
            case RDB_ENVVAL:
                return parseSpecialStringEncoded(firstByte & 0x3f, data);
            default:
                return null;
        }
    }
    private String parseSpecialStringEncoded(int type, DataInputStream data) throws IOException {
        int value;

        switch (type) {
            case RDB_STR_8BIT_ENC:
                value = readSignedByte(data);
                return String.valueOf(value);

            case RDB_STR_16BIT_ENC:
                value = readShort(data);
                return String.valueOf(value);

            case RDB_STR_32BIT_ENC:
                value = readInt(data);
                return String.valueOf(value);

            case RDB_STR_LZF_COMP:
                return parseLzfCompressedStr(data);

            default:
                throw new IllegalArgumentException("Unknown string special encoding " + type);
        }
    }
    private String parseLzfCompressedStr(DataInputStream data) throws IOException {
        int clen = readLength(data);
        int ulen = readLength(data);
        byte[] buffer = readBytes(clen,data);
        byte[] dest = new byte[ulen];
        LZF.expand(buffer, 0, dest, 0, ulen);
        return new String(dest, ASCII);
    }
}
