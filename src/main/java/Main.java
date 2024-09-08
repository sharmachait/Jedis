import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
  public static void main(String[] args){
//      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
//
//      context.scan("io.codecrafters");
//
//      RedisConfig config = context.getBean(RedisConfig.class);
//
//      for (int i = 0; i < args.length; i++)
//      {
//          switch (args[i])
//          {
//              case "--port":
//                  config.port = Integer.parseInt(args[i + 1]);
//                  break;
//              case "--replicaof":
//                  config.role = "slave";
//                  String masterHost = args[i + 1].split(" ")[0];
//                  int masterPort = Integer.parseInt(args[i + 1].split(" ")[1]);
//                  config.masterHost = masterHost;
//                  config.masterPort = masterPort;
//                  break;
//              case "--dir":
//                  config.dir = args[i + 1];
//                  break;
//              case "--dbfilename":
//                  config.dbfilename = args[i + 1];
//                  break;
//              default:
//                  break;
//          }
//      }

      TcpServer app =new TcpServer();
      System.out.println("starting");
      app.StartMaster();
  }
}

