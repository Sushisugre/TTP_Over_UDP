package datatypes;

import java.io.Serializable;

public class TTPSegment implements Serializable {

    /**
     * Use a large value here to save the trouble of calculate object size
     */
    public static final int HEADER_SIZE = 228;

    /**
     * max size of datagram is 1500, other datagram header file at least take 172 byte
     * so limit the TTPSegment size in a reasonable range
     */
    public static final int MAX_SEGMENT_SIZE = 1300;

    public static final int MAX_DATA_SIZE = MAX_SEGMENT_SIZE - HEADER_SIZE;

    public enum Type {
        SYN,
        ACK,
        SYN_ACK,
        FIN,
        FIN_ACK,
        DATA, // contains data, there's more following, needs reassemble
        EOF;  // contains data, and it's the last fragment
    }

    private Type type;
    private int seqNum;
    private int ackNum;
    private int size;
    private byte[] data;

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(int seqNum) {
        this.seqNum = seqNum;
    }

    public int getAckNum() {
        return ackNum;
    }

    public void setAckNum(int ackNum) {
        this.ackNum = ackNum;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
