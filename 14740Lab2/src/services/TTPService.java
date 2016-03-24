package services;

import datatypes.Datagram;
import datatypes.TTPSegment;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;


public class TTPService {
    // retransmission timer interval
    private int timeout;
    // unacked packet window size
    private int winSize;
    // underline facility for data transmission
    private DatagramService ds;
    private Hashtable<String, TTPConnection> connections;
    private Hashtable<String, TTPConnection> pendingConnection;
    private TTPService.ReceiverThread receiver;

    public TTPService(int winSize, int timeout, int port) throws SocketException{
        this.timeout = timeout;
        this.winSize = winSize;
        this.connections = new Hashtable<>();
        this.pendingConnection = new Hashtable<>();
        this.receiver = new ReceiverThread();
        this.ds = new DatagramService(port, 10);

        this.receiver.start();
    }

    /**
     * Put the new connection to table, which will be used for packet mapping in ReceiverThread
     *
     * @param tag  address:port
     * @param conn connection
     */
    private void addConnection(String tag, TTPConnection conn) {
        connections.put(tag, conn);
    }

    /**
     * When ReceiverThread receives a SYN segment, there isn't a connection built up at server side yet
     * So initiate a connection object and put it the pending table.
     * Server's accept method will pick up connection request from this table
     *
     * @param srcAddr source address
     * @param srcPort source port
     * @param dstAddr destination address
     * @param dstPort destination port
     * @return connection
     */
    public TTPConnection addPendingConnection(String srcAddr, short srcPort,
                                     String dstAddr, short dstPort) {
        TTPConnection conn = new TTPConnection(winSize, timeout, this);
        conn.setSrcAddr(srcAddr);
        conn.setSrcPort(srcPort);
        conn.setDstAddr(dstAddr);
        conn.setDstPort(dstPort);

        pendingConnection.put(conn.getSrcAddr()+":"+conn.getSrcPort(), conn);
        return conn;
    }

    /**
     * Accept a connection from client side
     *
     * @return connection
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public TTPConnection accept(String srcAddr, short srcPort) throws IOException, ClassNotFoundException{

        String key = srcAddr+":"+srcPort;
        while(!pendingConnection.containsKey(key));

        TTPConnection conn = pendingConnection.get(key);
        pendingConnection.remove(key);
        addConnection(conn.getTag(), conn);

        // loop to wait for syn
        while(!conn.isReceivedSYN());
        Datagram datagram = conn.retrieve(TTPSegment.Type.SYN);
        conn.setReceivedSYN(false);
        TTPSegment segment = (TTPSegment) datagram.getData();

        TTPSegment synack = packSegment(conn, TTPSegment.Type.SYN_ACK, segment.getSeqNum(), null);
        sendSegment(conn, synack);
        conn.setLastAcked(segment.getSeqNum());

        // wait to receive ACK of SYN_ACK
        while (conn.hasUnacked() && conn.firstUnacked() <= synack.getSeqNum())

        System.out.println("== Connection established ==");
        return conn;
    }

    /**
     * Establish connection with a server
     *
     * @param srcAddr source address
     * @param srcPort source port
     * @param dstAddr destination address
     * @param dstPort destination port
     * @return connection
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public TTPConnection connect(String srcAddr, short srcPort,
                                 String dstAddr, short dstPort)
                                throws IOException, ClassNotFoundException{

        TTPConnection conn = new TTPConnection(winSize,timeout,this);
        conn.setSrcAddr(srcAddr);
        conn.setSrcPort(srcPort);
        conn.setDstAddr(dstAddr);
        conn.setDstPort(dstPort);

        addConnection(conn.getTag(), conn);

        TTPSegment segment = packSegment(conn, TTPSegment.Type.SYN, 0, null);
        sendSegment(conn, segment);

        // wait for SYNACK
        while (!conn.isReceivedSYNACK());

        Datagram datagram = conn.retrieve(TTPSegment.Type.SYN_ACK);
//        TTPSegment synack = (TTPSegment) datagram.getData();

        while (conn.lastAcked() < TTPConnection.ISN);

        System.out.println("== Connection established ==");
        return conn;
    }

    /**
     * Close a connection
     * @param conn connection
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void close(TTPConnection conn) throws IOException, ClassNotFoundException{
        // send FIN

        TTPSegment fin = packSegment(conn, TTPSegment.Type.FIN, 0, null);

        boolean isSent = false;
        while (!isSent) {
           isSent = sendSegment(conn, fin);
        }


        while (conn.isReceivedSYN());

        connections.remove(conn.getTag());
    }

    /**
     * Method that allows application to send data through the connection
     * Large data array will be fragmented into small TTPSegment which can fit in a Datagram
     *
     * @param conn connection
     * @param data data
     * @throws IOException
     */
    public void send(TTPConnection conn, byte[] data) throws IOException{
        int length = data.length;
        int remain = length;

        // break data into fragments
        while (remain > 0) {
            int len;
            TTPSegment.Type type;
            if (remain + TTPSegment.HEADER_SIZE > TTPSegment.MAX_SEGMENT_SIZE) {
                len = TTPSegment.MAX_SEGMENT_SIZE - TTPSegment.HEADER_SIZE;
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

    /**
     * Helper method, construct Datagram and feed it to another helper method to send
     *
     * @param conn connection
     * @param segment TTPSegment that contains control info all application data
     * @return isSent - when the unack window is totally full, new segment need to wait
     * @throws IOException
     */
    private boolean sendSegment(TTPConnection conn, TTPSegment segment) throws IOException {

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

    /**
     * Send a datagram
     *
     * @param conn connection
     * @param datagram datagram
     * @throws IOException
     */
    void sentDatagram(TTPConnection conn, Datagram datagram) throws IOException{

            ds.sendDatagram(datagram);

            TTPSegment segment = (TTPSegment) datagram.getData();
            System.out.println("Send Segment: " + segment.getSeqNum() +" " + segment.getType().toString());

            if (segment.getType() != TTPSegment.Type.ACK) {
                if (!conn.hasUnacked())
                    conn.startTimer();
                conn.addToWindow(segment.getSeqNum(), datagram);
            }
    }

    /**
     * Method that allows application to receive data from the connection
     *
     * @param conn connection
     * @return application data
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public byte[] receive(TTPConnection conn) throws ClassNotFoundException, IOException{
        List<byte[]> fragments = new ArrayList<>();
        int length = 0;
        boolean isEnd = false;
        while (!isEnd) {

            while (!conn.hasData());

            Datagram datagram = conn.retrieveData();
            TTPSegment segment = (TTPSegment) datagram.getData();

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
     * Helper method to construct a specified type of segment
     *
     * @param conn connection
     * @param type segment type
     * @param ackNum which segment is this one acknowledge for, valid for ACK/SYN_ACK/FIN_ACK
     * @param data application data, valid for DATA/EOF
     * @return TTPSegment
     */
    private TTPSegment packSegment(TTPConnection conn, TTPSegment.Type type, int ackNum, byte[] data) {
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

    /**
     * Keep running in the ReceiverThread since TTPService is initiated
     * Receive a single TTPSegment, dispatch to different connections that associated with the TTPService instance
     *
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private void receiveSegment() throws ClassNotFoundException, IOException{

        Datagram datagram = ds.receiveDatagram();
        TTPSegment segment = (TTPSegment) datagram.getData();

        String key = datagram.getSrcaddr()+":"+datagram.getSrcport();
        System.out.println("Connection key: " + key);
        TTPConnection conn = connections.get(key);

        // if no matching connection in the connection table, and this segment is a SYN
        // create a new connection and put it in the pending table
        if (conn == null && segment.getType() == TTPSegment.Type.SYN) {
            conn = addPendingConnection(datagram.getDstaddr(),
                    datagram.getDstport(),
                    datagram.getSrcaddr(),
                    datagram.getSrcport());
        }

        // no available connections yet, return
        if (conn == null) {
            return;
        }

        System.out.println("Receive Segment: " + segment.getSeqNum()
                +" " + segment.getType().toString()
                + ", last acked " + conn.lastAcked());

        // checksum error
        if (!validateChecksum(datagram)) {
            System.err.println("Checksum error");
            sendAck(conn, conn.lastAcked());
            return;
        }

        // out of order
        if (segment.getSeqNum() != conn.lastAcked() + 1) {
            System.out.println("Out of order: expected - "+(conn.lastAcked()+1)+", got - " + segment.getSeqNum());
            sendAck(conn, conn.lastAcked());
            return;
        }

        switch (segment.getType()) {
            case ACK:
                while (!conn.hasUnacked());
                // cumulative ack, so the ack num may be larger than first unacked
                System.out.println("  ACK ackNum:"+segment.getAckNum()+", firstUnacked:"+conn.firstUnacked());
                handleACK(segment, conn);
                conn.setLastAcked(segment.getSeqNum());
                break;
            case SYN:
                conn.setReceivedSYN(true);
                break;
            case FIN:
                conn.setReceivedFIN(true);
                break;
            case SYN_ACK:
                conn.setReceivedSYNACK(true);
                System.out.println("  SYN ACK ackNum:"+segment.getAckNum()+", firstUnacked:"+conn.firstUnacked());
                handleACK(segment, conn);
                sendAck(conn, segment.getSeqNum());
                break;
            case FIN_ACK:
                conn.setReceivedFINACK(true);
                sendAck(conn, segment.getSeqNum());
                break;
            case DATA:
                sendAck(conn, segment.getSeqNum());
                break;
            case EOF:
                sendAck(conn, segment.getSeqNum());
                break;
            default:
                break;
        }

        conn.addToQueue(datagram);
    }

    /**
     * Handle connection timer and window after receiving an ACK
     *
     * @param segment segment
     * @param conn connection
     */
    private void handleACK(TTPSegment segment, TTPConnection conn) {
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
        }
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


    /**
     * A background thread that listen to all the data sent through the Datagram Socket
     * And distributed them to different Connections
     */
    class ReceiverThread extends Thread {

        TTPService ttpService;

        public ReceiverThread() {
            this.ttpService = TTPService.this;
        }

        @Override
        public void run() {
            System.out.println("Receiver thread started");
            while (!currentThread().isInterrupted()) {
                try {
                    ttpService.receiveSegment();

                } catch (IOException e){
                    e.printStackTrace();
                } catch (ClassNotFoundException e){
                    e.printStackTrace();
                }

            }
        }

    }

}
