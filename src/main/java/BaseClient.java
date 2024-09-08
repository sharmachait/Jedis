import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class BaseClient {
    public Socket socket;
    public InetSocketAddress remoteIpEndPoint;
    public InputStream inputStream;
    public OutputStream outputStream;
    public int port;
    public String ipAddress;
    public int id;


}
