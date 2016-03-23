package services;

import datatypes.TTPSegment;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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
    private ConcurrentSkipListMap<Integer, TTPSegment> unacked;

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
        timer = new Timer();
        nextSeq = ISN;
        lastAcked = ISN - 1;
    }

    public void startTimer() {
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
    }

    /**
     * Timeout, out of order, or checksum error
     * resend all the unacked packet in the window
     */
    public void resend() throws IOException{
        for(Map.Entry<Integer, TTPSegment> entry : unacked.entrySet()) {
            TTPSegment segment = entry.getValue();
            ttpService.sendSegment(this, segment);
        }
    }

    public boolean isWindowFull() {
        return unacked.size() == winSize;
    }

    public boolean hasUnacked() {
        return unacked.isEmpty();
    }

    public void moveWindowTo(int startSeq) {
        while (unacked.firstKey() < startSeq) {
            unacked.pollFirstEntry();
        }
    }

    public void addToWindow(TTPSegment segment) {
        unacked.put(segment.getSeqNum(), segment);
    }

    public int firstUnacked() {
        return unacked.firstKey();
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

    public DatagramService getDatagramService() {
        return ds;
    }

    public void setDatagramService(DatagramService ds) {
        this.ds = ds;
    }

    public TTPService getTtpService() {
        return ttpService;
    }

    public TTPService.ReceiverThread getReceiver() {
        return receiver;
    }

    public void setReceiver(TTPService.ReceiverThread receiver) {
        this.receiver = receiver;
    }

    public synchronized boolean isReceivedFIN() {
        return receivedFIN;
    }

    public synchronized void setReceivedFIN(boolean receivedFIN) {
        this.receivedFIN = receivedFIN;
    }

    public synchronized boolean isReceivedFINACK() {
        return receivedFINACK;
    }

    public synchronized void setReceivedFINACK(boolean receivedFINACK) {
        this.receivedFINACK = receivedFINACK;
    }

    public synchronized boolean isReceivedDATA() {
        return receivedDATA;
    }

    public synchronized void setReceivedDATA(boolean receivedDATA) {
        this.receivedDATA = receivedDATA;
    }

    public synchronized boolean isReceivedSYN() {
        return receivedSYN;
    }

    public synchronized void setReceivedSYN(boolean receivedSYN) {
        this.receivedSYN = receivedSYN;
    }


    public synchronized boolean isReceivedSYNACK() {
        return receivedSYNACK;
    }

    public synchronized void setReceivedSYNACK(boolean receivedSYNACK) {
        this.receivedSYNACK = receivedSYNACK;
    }
}
