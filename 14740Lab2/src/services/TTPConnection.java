package services;

import datatypes.Datagram;
import datatypes.TTPSegment;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Simulate a TCP socket between 2 hosts
 */
public class TTPConnection {

    // Initial Sequence Number
    public static final int ISN = 1024;
    // window size
    private int winSize;
    // retransmission timer interval
    private int timeout;
    // next sequence number to send
    private int nextSeq;
    // last acked segment - use when working as receiver
    private int lastAcked;

    // address info of source and destination
    private String srcAddr;
    private short srcPort;
    private String dstAddr;
    private short dstPort;

    // a handle to TTPService the connection uses
    private TTPService ttpService;

    // timer for oldest unacked packet
    private Timer timer;
    // key: seq number, value: datagram
    private ConcurrentSkipListMap<Integer, Datagram> unacked;
    // Queue which buffers the received DATA/EOF TTPSegment
    private ConcurrentLinkedQueue<Datagram> dataQueue;
    // Queue which buffers the received SYN/SYN_ACK/FIN/FIN_ACK TTPSegment
    private ConcurrentLinkedQueue<Datagram> controlQueue;

    // ReceiverThread will update this field
    // Which are used in spin lock to synchronize TTP flow
    private boolean receivedSYN;
    private boolean receivedFIN;
    private boolean receivedSYNACK;
    private boolean receivedFINACK;

    // if the connection is closed, change to false
    public boolean isActive;

    public TTPConnection(int winSize, int timeout, TTPService ttpService) {
        this.winSize = winSize;
        this.timeout = timeout;
        this.ttpService = ttpService;

        unacked = new ConcurrentSkipListMap<>();
        dataQueue = new ConcurrentLinkedQueue<>();
        controlQueue = new ConcurrentLinkedQueue<>();
        nextSeq = ISN;
        lastAcked = ISN - 1;
        isActive = true;
    }

    /**
     * Tag that identifies each connection that associated with a server/TTPService
     * @return tag
     */
    public String getTag() {
        return dstAddr + ":" +dstPort;
    }

    /**
     * Change the connection to be inactive
     */
    public void close(){
        isActive =false;
    }

    /**
     * Start or restart timer for oldest datagram in the window,
     * when timeout, resend all the packets in the window
     */
    public void startTimer() {
        System.out.println("  Start timer");
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    resend();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, timeout);
    }

    /**
     * Stop timer when receiving an valid ACK
     * Restart the timer if unacked window is not empty
     */
    public void endTimer(){
        System.out.println("  End timer");
        timer.cancel();
    }

    /**
     * Timeout, out of order, or checksum error
     * resend all the unacked packet in the window
     */
    public void resend() throws IOException{
        System.err.println("===> Timeout: Start to resent the segments in window...");

        endTimer();
        startTimer();
        for(Map.Entry<Integer, Datagram> entry : unacked.entrySet()) {
            Datagram datagram = entry.getValue();
            ttpService.sentDatagram(this, datagram);
        }
    }

    /**
     * Is window full? Can we send more packet without waiting for ACKs?
     * @return isWindowFull
     */
    public boolean isWindowFull() {
        return unacked.size() == winSize;
    }

    /**
     * Is there any packet haven't been ACKed in this connection?
     * @return hasUnacked
     */
    public boolean hasUnacked() {
        return !unacked.isEmpty();
    }

    /**
     * After received a valid ACK, slide the window
     * @param startSeq oldest unacknowledged packet
     */
    void moveWindowTo(int startSeq) {
        System.out.println("  Move window to "+startSeq);
        while (!unacked.isEmpty() && unacked.firstKey() < startSeq) {
            unacked.pollFirstEntry();
        }
    }

    /**
     * After send a packet, add it to the unacknowledged window
     * @param seqNum sequence number
     * @param datagram Datagram
     */
    public void addToWindow(int seqNum, Datagram datagram) {
        System.out.println("  Add "+seqNum+" to unacked window");
        unacked.put(seqNum, datagram);
    }

    /**
     * First unacknowledged packet in the window
     * @return seqNum
     */
    public int firstUnacked() {
        synchronized (unacked) {
            return unacked.firstKey();
        }
    }

    /**
     * ReceiverThread use this method to distribute received packets to the queues in different connections
     * @param datagram Datagram
     */
    void addToQueue(Datagram datagram) {

        TTPSegment segment = (TTPSegment) datagram.getData();

        if (segment.getType() == TTPSegment.Type.ACK) {
            // do not enqueue
            return;
        } else if (segment.getType() == TTPSegment.Type.DATA || segment.getType() == TTPSegment.Type.EOF) {
            dataQueue.offer(datagram);
        } else {
            controlQueue.offer(datagram);
        }

        System.out.println("  Add "+segment.getType().toString() +" segment to queue");
    }

    /**
     * Retrieve a certain type of packet from queue
     *
     * @param type TTPSegment type
     * @return Datagram that contains Segment of required type
     */
    Datagram retrieve(TTPSegment.Type type) {

        System.out.println("  Retrieve "+type.toString() +" segment from queue");

        if (type == TTPSegment.Type.ACK) {
            return null;
        } else if (type == TTPSegment.Type.DATA || type == TTPSegment.Type.EOF) {
            return dataQueue.poll();
        } else {
            while (controlQueue.isEmpty() || ((TTPSegment)controlQueue.peek().getData()).getType() != type);
            return controlQueue.poll();
        }
    }

    /**
     * Retrieve a DATA packet from queue
     * @return Datagram that contains a DATA TTPSegment
     */
    Datagram retrieveData() {

        Datagram datagram = dataQueue.poll();
        TTPSegment segment = (TTPSegment) datagram.getData();
        System.out.println("  Retrieve "+segment.getType().toString() +" segment from queue");
        return datagram;
    }

    public void setLastAcked(int seqNum) {
        lastAcked = seqNum;
    }

    public int lastAcked() {
        return lastAcked;
    }

    public int getNextSeq() {
        return nextSeq++;
    }

    public String getSrcAddr() {
        return srcAddr;
    }

    public void setSrcAddr(String srcAddr) {
        this.srcAddr = srcAddr;
    }

    public short getSrcPort() {
        return srcPort;
    }

    public void setSrcPort(short srcPort) {
        this.srcPort = srcPort;
    }

    public String getDstAddr() {
        return dstAddr;
    }

    public void setDstAddr(String dstAddr) {
        this.dstAddr = dstAddr;
    }

    public short getDstPort() {
        return dstPort;
    }

    public void setDstPort(short dstPort) {
        this.dstPort = dstPort;
    }

    synchronized boolean isReceivedFIN() {
        return receivedFIN;
    }

    synchronized void setReceivedFIN(boolean receivedFIN) {
        this.receivedFIN = receivedFIN;
    }

    synchronized boolean isReceivedFINACK() {
        return receivedFINACK;
    }

    synchronized void setReceivedFINACK(boolean receivedFINACK) {
        this.receivedFINACK = receivedFINACK;
    }

    boolean hasData() {
        return !dataQueue.isEmpty();
    }

    synchronized boolean isReceivedSYN() {
        return receivedSYN;
    }

    synchronized void setReceivedSYN(boolean receivedSYN) {
        this.receivedSYN = receivedSYN;
    }


    synchronized boolean isReceivedSYNACK() {
        return receivedSYNACK;
    }

    synchronized void setReceivedSYNACK(boolean receivedSYNACK) {
        this.receivedSYNACK = receivedSYNACK;
    }


}
