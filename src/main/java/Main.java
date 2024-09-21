import Components.RedisConfig;
import Components.TcpServer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.*;

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

      String filePath = config.dir+"/"+config.dbfilename;
      File file = new File(filePath);
      if (file.exists() && !file.isDirectory()) {
          // Read data from the file
          try (FileReader fileReader = new FileReader(file)) {
              int character;
              StringBuilder fileContent = new StringBuilder();
              System.out.println("====================================Reading file: " + filePath);
              while ((character = fileReader.read()) != -1) {
                  char c = (char) character;
                  fileContent.append(c);
                  System.out.print(character);
              }
              System.out.println();
          } catch (FileNotFoundException e) {
              throw new RuntimeException(e);
          } catch (IOException e) {
              throw new RuntimeException(e);
          }
      }

      TcpServer app =context.getBean(TcpServer.class);

      if(config.role.equals("master")){
          app.StartMaster();
      }
      else {
          app.StartSlave();
      }
  }
}

