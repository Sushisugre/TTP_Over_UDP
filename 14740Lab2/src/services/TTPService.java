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
        conn.setReceiver(receiver);


        DatagramService ds = new DatagramService(srcPort, 10);
        conn.setDatagramService(ds);
        receiver.start();

        // loop to wait for syn
        while(!conn.isReceivedSYN());
        Datagram datagram = conn.retrieve(TTPSegment.Type.SYN);
        conn.setReceivedSYN(false);

        TTPSegment segment = (TTPSegment) datagram.getData();
        conn.setSrcAddr(datagram.getDstaddr());
        conn.setSrcPort(datagram.getDstport());
        conn.setDstAddr(datagram.getSrcaddr());
        conn.setDstPort(datagram.getSrcport());
        conn.initTimer();

        TTPSegment synack = packSegment(conn, TTPSegment.Type.SYN_ACK, segment.getSeqNum(), null);
        sendSegment(conn, synack);
        conn.setLastAcked(segment.getSeqNum());

        System.out.println("Connection established");
        return conn;
    }

    public TTPConnection connect(String srcAddr, short srcPort,
                                 String dstAddr, short dstPort,
                                 int verbose)
                                throws IOException, ClassNotFoundException{

        DatagramService ds = new DatagramService(srcPort, verbose);
        TTPConnection conn = new TTPConnection(winSize,timeout,this);
        ReceiverThread receiver = new ReceiverThread(conn);
        conn.setReceiver(receiver);
        conn.setDatagramService(ds);
        receiver.start();

        conn.setSrcAddr(srcAddr);
        conn.setSrcPort(srcPort);
        conn.setDstAddr(dstAddr);
        conn.setDstPort(dstPort);
        conn.initTimer();

        TTPSegment segment = packSegment(conn, TTPSegment.Type.SYN, 0, null);
        sendSegment(conn, segment);

        // wait for SYNACK
        while (!conn.isReceivedSYNACK());

        System.out.println("Connection established");
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
        int length = data.length;
        int remain = length;

        while (remain > 0) {
            int len;
            TTPSegment.Type type;
            if (remain + TTPSegment.HEADER_SIZE > MAX_PAYLOAD_SIZE) {
                len = MAX_PAYLOAD_SIZE - TTPSegment.HEADER_SIZE;
                type = TTPSegment.Type.DATA;
            } else {
                len = remain;
                type = TTPSegment.Type.EOF;
            }

            byte[] fagment = new byte[len];
            System.arraycopy(data, length - remain, fagment, 0, len);

            TTPSegment segment = packSegment(conn, type, 0, fagment);

            // loop until there's space available in send window
            boolean isSent = false;
            while (!isSent) {
                isSent = sendSegment(conn, segment);
            }
            remain -= len;
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
        datagram.setChecksum((short) 0);
        datagram.setChecksum(DataUtil.getUDPCheckSum(DataUtil.objectToByte(datagram)));

        sentDatagram(conn, datagram);

        return true;
    }

    void sentDatagram(TTPConnection conn, Datagram datagram) throws IOException{
        DatagramService ds = conn.getDatagramService();
        ds.sendDatagram(datagram);

        TTPSegment segment = (TTPSegment) datagram.getData();
        System.out.println("Send Segment: " + segment.getSeqNum() +" " + segment.getType().toString());

        if (segment.getType() != TTPSegment.Type.ACK) {
            if (!conn.hasUnacked())
                conn.startTimer();
            conn.addToWindow(segment.getSeqNum(), datagram);
        }
    }

    public byte[] receive(TTPConnection conn) throws ClassNotFoundException, IOException{
        List<byte[]> fragments = new ArrayList<>();
        int length = 0;
        boolean isEnd = false;
        while (!isEnd) {
            Datagram datagram = conn.retrieveData();
            TTPSegment segment = (TTPSegment) datagram.getData();
//            TTPSegment segment = receiveSegment(conn);
            // receive corrupted or out of order segment
            if (segment == null) continue;

            if (segment.getType() == TTPSegment.Type.EOF) {
                isEnd = true;
            }

            length += segment.getData().length;
            fragments.add(segment.getData());
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

        System.out.println("Receive Segment: " + segment.getSeqNum() +" " + segment.getType().toString()
            + ", last acked " + conn.lastAcked());


        // checksum error
        if (!validateChecksum(datagram)) {
            System.err.println("Checksum error");
            sendAck(conn, conn.lastAcked());
            return null;
        }

        // out of order
        if (segment.getSeqNum() != conn.lastAcked() + 1) {
            System.out.println("Out of order: expected - "+(conn.lastAcked()+1)+", got - " + segment.getSeqNum());
            sendAck(conn, conn.lastAcked());
            return null;
        }

        switch (segment.getType()) {
            case ACK:
                // cumulative ack, so the ack num may be larger than first unacked
                if (segment.getAckNum() >= conn.firstUnacked()) {
                    conn.moveWindowTo(segment.getAckNum() + 1);
                    if (conn.hasUnacked()) {
                        conn.endTimer();
                        conn.startTimer();
                    } else {
                        conn.endTimer();
                    }
                } else { // duplicated ACK
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
                if (segment.getAckNum() >= conn.firstUnacked()) {
                    conn.moveWindowTo(segment.getAckNum() + 1);
                    if (conn.hasUnacked()) {
                        conn.endTimer();
                        conn.startTimer();
                    } else {
                        conn.endTimer();
                    }
                } else {
                    conn.endTimer();
                    return null;
                }
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

        conn.addToQueue(datagram);
        return segment;
    }

    public TTPSegment packSegment(TTPConnection conn, TTPSegment.Type type, int ackNum, byte[] data) {
        TTPSegment segment = new TTPSegment();

        segment.setType(type);
        segment.setSeqNum(conn.getNextSeq());
        segment.setData(data);
        if(data != null)segment.setSize(data.length);
        if (type == TTPSegment.Type.ACK
                || type == TTPSegment.Type.SYN_ACK
                || type == TTPSegment.Type.FIN_ACK)
            segment.setAckNum(ackNum);

        return segment;
    }

    private void sendAck(TTPConnection conn, int endSeq) throws IOException{
        System.out.println("Sending ACK for seqNum: "+endSeq);
        TTPSegment segment = packSegment(conn, TTPSegment.Type.ACK, endSeq, null);
        sendSegment(conn, segment);
        conn.setLastAcked(endSeq);
    }

    private boolean validateChecksum(Datagram datagram){
        short expected = datagram.getChecksum();
        datagram.setChecksum((short) 0);
        byte[] received = DataUtil.objectToByte(datagram);
        return expected == DataUtil.getUDPCheckSum(received);
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
