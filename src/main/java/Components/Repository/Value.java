package Components.Repository;

import java.time.LocalDateTime;
import java.util.concurrent.LinkedBlockingDeque;

public class Value {
    public ValueType type;
    public LinkedBlockingDeque<String> list;
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
}
