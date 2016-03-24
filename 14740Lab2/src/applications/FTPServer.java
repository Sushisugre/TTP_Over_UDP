package applications;

import datatypes.FTPData;
import datatypes.FTPMeta;
import services.DataUtil;
import services.TTPConnection;
import services.TTPService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple FTPServer that handler file request
 */
public class FTPServer {

    // Chunk large file into small pieces, shouldn't dump large file in memory
    private static final int CHUNK_SIZE = 1024 * 512;
    // Thread pool for handling client requests
    private static ExecutorService threadPool = Executors.newFixedThreadPool(30);
    // TTPServices associated with server
    private static TTPService ttpService;


    public static void main(String[] args) throws SocketException{
        if(args.length != 3) {
            printUsage();
        }

        System.out.println("Starting FTPServer ...");

        int port = Integer.parseInt(args[0]);
        int winSize = Integer.parseInt(args[1]);
        int timeout = Integer.parseInt(args[2]);

        ttpService = new TTPService(winSize, timeout, port);

        while (true) {
            try{

                // receive a connection
                TTPConnection conn = ttpService.accept("127.0.0.1", (short) port);
                System.out.println("Server: got connection from " + conn.getTag());

                // send the connection to handler thread
                RequestHandler handler = new RequestHandler(conn);
                threadPool.execute(handler);
//                Thread.sleep(100000000);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Handler that take care of different clients
     */
    static class RequestHandler implements Runnable {

        private TTPConnection conn;

        public RequestHandler(TTPConnection conn) {
            this.conn = conn;
        }

        @Override
        public void run() {

            try {

                while (conn.isActive) {

                    System.out.println("Server: request handler started");
                    String path = (String)DataUtil.byteToObject(ttpService.receive(conn));
                    System.out.println("Server: receive request for - " + path);

                    File file = new File(path);
                    boolean isFound = file.exists();
                    System.out.println("Server: " + path +" found? " + isFound);

                    FTPMeta meta = new FTPMeta();
                    meta.setPath(path);
                    meta.setFound(isFound);

                    if (!isFound) {
                        ttpService.send(conn, DataUtil.objectToByte(meta));
                        ttpService.close(conn);
                        System.exit(0);
                    }

                    meta.setTotalSize((int)file.length());
                    meta.setMd5Checksum(DataUtil.getMD5Checksum(path));
                    ttpService.send(conn, DataUtil.objectToByte(meta));
                    System.out.println("Server: send file meta");

                    int remain = (int)file.length();
                    int retVal = 0;
                    byte[] buffer = new byte[CHUNK_SIZE];
                    BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));

                    // loop to send the chunks of requested file
                    while (remain > 0 && retVal != -1) {

                        int length = remain > CHUNK_SIZE? CHUNK_SIZE:remain;
                        retVal = input.read(buffer, 0, length);

                        if(retVal > 0) {
                            byte[] data = new byte[retVal];
                            System.arraycopy(buffer, 0, data, 0, retVal);

                            FTPData ftpData = new FTPData();
                            ftpData.setPath(path);
                            ftpData.setData(data);
                            ftpData.setSize(retVal);

                            ttpService.send(conn, DataUtil.objectToByte(ftpData));
                            remain -= retVal;
                        }
                    }

                    System.out.println("Server: Finished transmission");
                }


            } catch (FileNotFoundException e){
                e.printStackTrace();
            } catch (SocketException e) {
                System.err.println("Client: closed connection.");
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }


    private static void printUsage() {
        System.out.println("Usage: java FTPServer <port> <win_size> <timeout>");
        System.exit(-1);
    }
}


