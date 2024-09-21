import Components.RedisConfig;
import Components.TcpServer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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

      String filePath = config.dir+"\\"+config.dbfilename;
      File file = new File(filePath);
      System.out.println("Checking file -------------------------------------------------------------------------------");
      if (file.exists() && !file.isDirectory()) {
          System.out.println("File exists. Reading data from it...");
          // Read data from the file
          try (BufferedReader br = new BufferedReader(new FileReader(file))) {
              String line;
              //parse rdb file populate the dictionary
              while ((line = br.readLine()) != null) {
                  System.out.println(line);
              }
          } catch (IOException e) {
              System.out.println("An error occurred while reading the file: " + e.getMessage());
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

