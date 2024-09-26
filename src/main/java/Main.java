import Components.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class Main {
  public static void main(String[] args){

      AnnotationConfigApplicationContext context =
              new AnnotationConfigApplicationContext(AppConfig.class);
      RedisConfig config = context.getBean(RedisConfig.class);
      RdbParser rdbParser = context.getBean(RdbParser.class);

      for (int i = 0; i < args.length; i++)
      {
          switch (args[i])
          {
              case "--port":
                  config.port = Integer.parseInt(args[i + 1]);
                  break;
              case "--replicaof":
                  config.role = "slave";
                  String masterHost = args[i + 1].split(" ")[0];
                  int masterPort = Integer.parseInt(args[i + 1].split(" ")[1]);
                  config.masterHost = masterHost;
                  config.masterPort = masterPort;
                  break;
              case "--dir":
                  config.dir = args[i + 1];
                  break;
              case "--dbfilename":
                  config.dbfilename = args[i + 1];
                  break;
              default:
                  break;
          }
      }

      String filePath = config.dir+"/"+config.dbfilename;
      File file = new File(filePath);

      if (!file.exists() || file.isDirectory()) {
          System.out.println("RDB file not found");
          return;
      }

      try (DataInputStream dataStream = new DataInputStream(new FileInputStream(filePath))) {
          List<KeyValuePair> data = rdbParser.parse(dataStream);
          for(KeyValuePair kvp : data){
              System.out.println(kvp.getKey()+":"+kvp.getValue().toString());
          }
          Store store = context.getBean(Store.class);
          populateStore(data, store);
          dataStream.close();
      } catch (FileNotFoundException e) {
          throw new RuntimeException(e);
      } catch (IOException e) {
          throw new RuntimeException(e);
      }

      TcpServer app =context.getBean(TcpServer.class);

      if(config.role.equals("master")){
          app.StartMaster();
      }
      else {
          app.StartSlave();
      }
  }
  private static void populateStore(List<KeyValuePair> data, Store store){
      for(KeyValuePair kvp : data){
          LocalDateTime expiry = LocalDateTime.of(999999999, 12, 31, 23, 59, 59, 999999999);
          if(kvp.getExpiryTime() != null)
            expiry = kvp.getExpiryTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
          String val = kvp.getValue().toString();
          LocalDateTime curr = LocalDateTime.now();
          Value value = new Value(val, curr, expiry);
          store.map.put(kvp.getKey(), value);
      }
  }
}

