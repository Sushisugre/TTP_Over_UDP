package services;

import datatypes.Datagram;
import datatypes.TTPSegment;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;


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
    private LinkedBlockingQueue<Datagram> dataQueue;
    private LinkedBlockingQueue<Datagram> controlQueue;

    private DatagramService ds;

    private boolean receivedDATA;
    private boolean receivedSYN;
    private boolean receivedFIN;
    private boolean receivedSYNACK;
    private boolean receivedFINACK;


    public TTPConnection(int winSize, int timeout, TTPService ttpService) {
        this.winSize = winSize;
        this.timeout = timeout;
        this.ttpService = ttpService;

        unacked = new ConcurrentSkipListMap<>();
        dataQueue = new LinkedBlockingQueue<>();
        controlQueue = new LinkedBlockingQueue<>();
        nextSeq = ISN;
        lastAcked = ISN - 1;
    }

    public void initTimer() {
        timer = new Timer();
    }

    public void startTimer() {
        System.out.println("Start timer");
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
        while (unacked.firstKey() < startSeq) {
            System.out.println("First KEY " + unacked.firstKey());
            unacked.pollFirstEntry();
        }
    }

    public void addToWindow(int seqNum, Datagram datagram) {
        System.out.println("  Add "+seqNum+" to unacked window");
        unacked.put(seqNum, datagram);
    }

    public int firstUnacked() {
        return unacked.firstKey();
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

    public synchronized void setReceivedFIN(boolean receivedFIN) {
        this.receivedFIN = receivedFIN;
    }

    synchronized boolean isReceivedFINACK() {
        return receivedFINACK;
    }

    synchronized void setReceivedFINACK(boolean receivedFINACK) {
        this.receivedFINACK = receivedFINACK;
    }

    public synchronized boolean isReceivedDATA() {
        return receivedDATA;
    }

    public synchronized void setReceivedDATA(boolean receivedDATA) {
        this.receivedDATA = receivedDATA;
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
        System.out.println("  Add "+segment.getType().toString() +" segment to queue");

        if (segment.getType() == TTPSegment.Type.ACK) {
            // do not enqueue
        } else if (segment.getType() == TTPSegment.Type.DATA || segment.getType() == TTPSegment.Type.EOF) {
            dataQueue.offer(datagram);
        } else {
            controlQueue.offer(datagram);
        }
    }

    Datagram retrieve(TTPSegment.Type type) {

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
}
