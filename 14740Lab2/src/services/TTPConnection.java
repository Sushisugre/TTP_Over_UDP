package services;

import datatypes.Datagram;
import datatypes.TTPSegment;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;


public class TTPConnection {

    private static final int ISN = 1024;

    private int winSize;
    private int timeout;
    private int nextSeq;
    private int lastAcked;

    private String srcAddr;
    private short srcPort;
    private String dstAddr;
    private short dstPort;

    private TTPService.ReceiverThread receiver;
    private TTPService ttpService;

    // timer for oldest unacked packet
    private Timer timer;
    // key: seq number, value: datagram
    private ConcurrentSkipListMap<Integer, Datagram> unacked;
    private ConcurrentLinkedQueue<Datagram> dataQueue;
    private ConcurrentLinkedQueue<Datagram> controlQueue;

    private DatagramService ds;

    private boolean receivedSYN;
    private boolean receivedFIN;
    private boolean receivedSYNACK;
    private boolean receivedFINACK;

    private int pendingACK;


    public TTPConnection(int winSize, int timeout, TTPService ttpService) {
        this.winSize = winSize;
        this.timeout = timeout;
        this.ttpService = ttpService;

        unacked = new ConcurrentSkipListMap<>();
        dataQueue = new ConcurrentLinkedQueue<>();
        controlQueue = new ConcurrentLinkedQueue<>();
        nextSeq = ISN;
        lastAcked = ISN - 1;
    }

    ConcurrentSkipListMap<Integer, Datagram> getUnacked() {
        return unacked;
    }

    public void initTimer() {
        timer = new Timer();
    }

    public void startTimer() {
        System.out.println("  Start timer");
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

    public void endTimer(){
        System.out.println("  End timer");
        timer.cancel();
        initTimer();
    }

    /**
     * Timeout, out of order, or checksum error
     * resend all the unacked packet in the window
     */
    public void resend() throws IOException{
        System.err.println("Timeout: Start to resent the segments in window...");

        endTimer();
        startTimer();
        for(Map.Entry<Integer, Datagram> entry : unacked.entrySet()) {
            Datagram datagram = entry.getValue();
            ttpService.sentDatagram(this, datagram);
        }
    }

    public boolean isWindowFull() {
        return unacked.size() == winSize;
    }

    public boolean hasUnacked() {
        return !unacked.isEmpty();
    }

    void moveWindowTo(int startSeq) {
        System.out.println("  Move window to "+startSeq);
        while (!unacked.isEmpty() && unacked.firstKey() < startSeq) {
            unacked.pollFirstEntry();
//            System.out.println("  First KEY " + unacked.firstKey());
        }
    }

    public void addToWindow(int seqNum, Datagram datagram) {
        System.out.println("  Add "+seqNum+" to unacked window");
        unacked.put(seqNum, datagram);
    }

    public int firstUnacked() {
        synchronized (unacked) {
            return unacked.firstKey();
        }
    }

    public void setLastAcked(int seqNum) {
        System.out.println("  Set last acked " + seqNum);
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

    DatagramService getDatagramService() {
        return ds;
    }

    void setDatagramService(DatagramService ds) {
        this.ds = ds;
    }

    public TTPService getTtpService() {
        return ttpService;
    }

    TTPService.ReceiverThread getReceiver() {
        return receiver;
    }

    void setReceiver(TTPService.ReceiverThread receiver) {
        this.receiver = receiver;
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

    public Datagram retrieve(TTPSegment.Type type) {

        System.out.println("  Retrieve "+type.toString() +" segment from queue");

        if (type == TTPSegment.Type.ACK) {
            return null;
        } else if (type == TTPSegment.Type.DATA || type == TTPSegment.Type.EOF) {
            return dataQueue.poll();
        } else {
            return controlQueue.poll();
        }
    }

    Datagram retrieveData() {

        Datagram datagram = dataQueue.poll();
        TTPSegment segment = (TTPSegment) datagram.getData();
        System.out.println("  Retrieve "+segment.getType().toString() +" segment from queue");
        return datagram;
    }

    void waitForACK(int ackNum) {
        pendingACK = ackNum;
    }

    int waitingForACK() {
//        if (pendingACK > 0) {
            System.out.println("  Waiting for ACK for " + pendingACK);
//        }
        return pendingACK;
    }
}
