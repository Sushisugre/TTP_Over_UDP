package datatypes;

import java.io.Serializable;

/**
 * The file meta data server sends to client before transmitting the file
 */
public class FTPMeta implements Serializable {

    private boolean isFound;
    private String path;
    private int totalSize;
    private String md5Checksum;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMd5Checksum() {
        return md5Checksum;
    }

    public void setMd5Checksum(String md5Checksum) {
        this.md5Checksum = md5Checksum;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }

    public boolean isFound() {
        return isFound;
    }

    public void setFound(boolean found) {
        isFound = found;
    }
}
