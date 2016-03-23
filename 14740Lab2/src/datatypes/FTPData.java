package datatypes;

import java.io.Serializable;

/**
 * Created by s4keng on 3/21/16.
 */
public class FTPData implements Serializable {

    private String path;
    private byte[] data;
    private int size;


    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
