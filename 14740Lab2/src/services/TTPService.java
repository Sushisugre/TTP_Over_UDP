package services;

import datatypes.Datagram;
import datatypes.TTPSegment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class TTPService {
    public static final int MAX_PAYLOAD_SIZE = 1460;

    private int timeout;
    private int winSize;
    private int srcPort;


    public TTPService(int winSize, int timeout, int port) {
        this.timeout = timeout;
        this.winSize = winSize;
        this.srcPort = port;
    }

    /**
     * Accept a connection from client side
     * @return connection
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public TTPConnection accept() throws IOException, ClassNotFoundException{
        TTPConnection conn = new TTPConnection(winSize, timeout, this);
        ReceiverThread receiver = new ReceiverThread(conn);
        receiver.start();
        conn.setReceiver(receiver);


        DatagramService ds = new DatagramService(srcPort, 10);
        Datagram datagram = ds.receiveDatagram();
        conn.setDatagramService(ds);

        boolean isSYN = false;

        // loop to wait for syn
        while(!conn.isReceivedSYN());
        conn.setReceivedSYN(false);

        TTPSegment segment = null;
        while (!isSYN) {
            segment = (TTPSegment) datagram.getData();

            if (segment.getType() != TTPSegment.Type.SYN) {
                continue;
            }

            conn.setSrcAddr(datagram.getDstaddr());
            conn.setSrcPort(datagram.getDstport());
            conn.setDstAddr(datagram.getSrcaddr());
            conn.setDstPort(datagram.getSrcport());
            isSYN = true;
        }

        TTPSegment synack = packSegment(conn, TTPSegment.Type.SYN_ACK, segment.getAckNum(), null);
        sendSegment(conn, synack);




        return conn;
    }

    public TTPConnection connect(String srcAddr, short srcPort,
                                 String dstAddr, short dstPort,
                                 int verbose)
                                throws IOException, ClassNotFoundException{

        DatagramService ds = new DatagramService(srcPort, verbose);
        TTPConnection conn = new TTPConnection(winSize,timeout,this);
        ReceiverThread receiver = new ReceiverThread(conn);
        receiver.start();
        conn.setReceiver(receiver);
        conn.setDatagramService(ds);

        conn.setSrcAddr(srcAddr);
        conn.setSrcPort(srcPort);
        conn.setDstAddr(dstAddr);
        conn.setDstPort(dstPort);

        TTPSegment segment = packSegment(conn, TTPSegment.Type.SYN, 0, null);
        sendSegment(conn, segment);

        // wait for SYNACK

        return conn;
    }


    public void close(TTPConnection conn) throws IOException, ClassNotFoundException{
        // send FIN

        TTPSegment fin = packSegment(conn, TTPSegment.Type.FIN, 0, null);

        boolean isSent = false;
        while (!isSent) {
           isSent = sendSegment(conn, fin);
        }

        while (conn.isReceivedSYN());

        // stop thread
        conn.getReceiver().interrupt();

    }

    public void send(TTPConnection conn, byte[] data) throws IOException{
        if (data.length > MAX_PAYLOAD_SIZE) {

        }

        TTPSegment segment = new TTPSegment();
        boolean isSent = false;

        // loop until there's space available in send window
        while (!isSent) {
            isSent = sendSegment(conn, segment);
        }
    }

    public boolean sendSegment(TTPConnection conn, TTPSegment segment) throws IOException {
        if (conn.isWindowFull()) return false;

        Datagram datagram = new Datagram();
        datagram.setData(segment);
        datagram.setSrcaddr(conn.getSrcAddr());
        datagram.setDstaddr(conn.getDstAddr());
        datagram.setDstport(conn.getDstPort());
        datagram.setSrcport(conn.getSrcPort());
        datagram.setSize((short) DataUtil.objectToByte(segment).length);
        datagram.setChecksum(DataUtil.getUDPCheckSum(DataUtil.objectToByte(datagram)));

        DatagramService ds = conn.getDatagramService();
        ds.sendDatagram(datagram);

        if (segment.getType() != TTPSegment.Type.ACK) {
            if (!conn.hasUnacked()) conn.startTimer();
            conn.addToWindow(segment);
        }

        return true;
    }

    public byte[] receive(TTPConnection conn) throws ClassNotFoundException, IOException{
        List<byte[]> fragments = new ArrayList<>();
        int length = 0;
        boolean isEnd = false;
        while (!isEnd) {
            TTPSegment segment = receiveSegment(conn);
            // receive corrupted or out of order segment
            if (segment == null) continue;

            if (segment.getType() == TTPSegment.Type.EOF) {
                isEnd = true;
            }
            if (segment.getType() == TTPSegment.Type.DATA
                    || segment.getType() == TTPSegment.Type.EOF) {
                length += segment.getData().length;
                fragments.add(segment.getData());
            }
        }

        // reassemble fragments
        byte[] data = new byte[length];
        int pos = 0;
        for (byte[] frag: fragments){
            System.arraycopy(frag, 0, data, pos, frag.length);
            pos += frag.length;
        }

        return data;
    }

    /**
     * Receive single TTPSegment
     * @param conn connection
     * @return
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private TTPSegment receiveSegment(TTPConnection conn) throws ClassNotFoundException, IOException{

        DatagramService ds = conn.getDatagramService();
        Datagram datagram = ds.receiveDatagram();
        TTPSegment segment = (TTPSegment) datagram.getData();

        // checksum error
        if (!validateChecksum(datagram)) {
            sendAck(conn, conn.lastAcked());
            return null;
        }

        // out of order
        if (segment.getSeqNum() != conn.lastAcked() + 1) {
            sendAck(conn, conn.lastAcked());
            return null;
        }

        switch (segment.getType()) {
            case ACK:
                // cumulative ack, so the ack num may be larger than first unacked
                if (segment.getAckNum() >= conn.firstUnacked()) {
                    conn.moveWindowTo(segment.getAckNum() + 1);
                    if (conn.hasUnacked()) {
                        conn.startTimer();
                    } else {
                        conn.endTimer();
                    }
                } else {
                    conn.endTimer();
                    return null;
                }
                break;
            case SYN:
                conn.setReceivedSYN(true);
                break;
            case FIN:
                conn.setReceivedFIN(true);
                break;
            case SYN_ACK:
                conn.setReceivedSYNACK(true);
                sendAck(conn, segment.getSeqNum());
                break;
            case FIN_ACK:
                conn.setReceivedFINACK(true);
                sendAck(conn, segment.getSeqNum());
                break;
            case DATA:
                conn.setReceivedDATA(true);
                sendAck(conn, segment.getSeqNum());
                break;
            case EOF:
                conn.setReceivedDATA(true);
                sendAck(conn, segment.getSeqNum());
                break;
            default:
                break;
        }

        return segment;
    }

    public TTPSegment packSegment(TTPConnection conn, TTPSegment.Type type, int ackNum, byte[] data) {
        TTPSegment segment = new TTPSegment();

        segment.setType(type);
        segment.setSeqNum(conn.getNextSeq());
        segment.setData(data);
        segment.setSize(data.length);
        if (type == TTPSegment.Type.ACK
                || type == TTPSegment.Type.SYN_ACK
                || type == TTPSegment.Type.FIN_ACK)
            segment.setAckNum(ackNum);

        return segment;
    }

    private void sendAck(TTPConnection conn, int endSeq) throws IOException{
        TTPSegment segment = packSegment(conn, TTPSegment.Type.ACK, endSeq, null);
        sendSegment(conn, segment);
        conn.setLastAcked(endSeq);
    }

    private boolean validateChecksum(Datagram datagram){
        byte[] received = DataUtil.objectToByte(datagram);
        return DataUtil.getUDPCheckSum(received) == -1;
    }

    static class ReceiverThread extends Thread {

        TTPConnection conn;
        TTPService ttpService;

        public ReceiverThread(TTPConnection conn) {
            this.conn = conn;
            this.ttpService = conn.getTtpService() ;
        }

        @Override
        public void run() {
            System.out.println("Receiver thread started");
            while (!currentThread().isInterrupted()) {
                try {
                    ttpService.receiveSegment(conn);
                } catch (IOException e){
                } catch (ClassNotFoundException e){}

            }
        }

    }

}
