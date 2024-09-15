package Components;

public class ResponseDTO {
    public String response = null;
    public byte[] data = null;

    public ResponseDTO(String response)
    {
        this.response = response;
    }
    public ResponseDTO(String response, byte[] data)
    {
        this.data = data;
        this.response = response;
    }
}
