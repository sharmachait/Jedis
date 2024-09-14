import Components.RedisConfig;
import Components.TcpServer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
  public static void main(String[] args){

      AnnotationConfigApplicationContext context =
              new AnnotationConfigApplicationContext(AppConfig.class);
      RedisConfig config = context.getBean(RedisConfig.class);

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

      TcpServer app =context.getBean(TcpServer.class);

      if(config.role.equals("master")){
          app.StartMaster();
      }
      else {
          //
      }
  }
}

