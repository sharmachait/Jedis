package Components.Repository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingDeque;

public class Value {
    public ValueType type;
    public LinkedBlockingDeque<String> list;
    public TreeMap<String, Map<String, String>> stream;
    public String val;
    public LocalDateTime created;
    public LocalDateTime expiry;
    public boolean isDeletedInTransaction;// flag to delete after transaction
    public Value(String val, LocalDateTime created, LocalDateTime expiry) {
        this.created = created;
        this.val = val;
        this.expiry = expiry;
        this.isDeletedInTransaction = false;
        this.list = null;
        this.type = ValueType.STRING;
    }

    public Value(ValueType type, LocalDateTime created, LocalDateTime expiry) {
      this.created = created;
      this.val = null;
      this.expiry = expiry;
      this.type = type;
      this.list = new LinkedBlockingDeque<String>();

    }
    @Override
    public String toString(){
      return val;
    }
    private Value(){}
    public static Value newStream(){
        Value value = new Value();
        value.created = LocalDateTime.now();
        value.expiry = LocalDateTime.MAX;
        value.val = null;
        value.list = null;
        value.type = ValueType.STREAM;
        Comparator<String> streamIdComparator = (a,b) -> {
            String[] aParts = a.split("-");
            String[] bParts = b.split("-");
            long aMs = Long.parseLong(aParts[0]);
            long bMs = Long.parseLong(bParts[0]);
            if(aMs != bMs) return Long.compare(aMs, bMs);

            long aSeqid = Long.parseLong(aParts[1]);
            long bSeqid = Long.parseLong(bParts[1]);

            return Long.compare(aSeqid, bSeqid);
        };
        value.stream = new TreeMap<>(streamIdComparator);
        return value;
    }
}
