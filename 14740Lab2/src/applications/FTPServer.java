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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FTPServer {

    private static final int CHUNK_SIZE = 1024 * 512;
    private static ExecutorService threadPool =
            Executors.newFixedThreadPool(30);
    private static TTPService ttpService;



    public static void main(String[] args){
        if(args.length != 3) {
            printUsage();
        }

        System.out.println("Starting FTPServer ...");

        int port = Integer.parseInt(args[0]);
        int winSize = Integer.parseInt(args[1]);
        int timeout = Integer.parseInt(args[1]);

        ttpService = new TTPService(winSize, timeout, port);

        while (true) {
            try{
                TTPConnection conn = ttpService.accept();
                RequestHandler handler = new RequestHandler(conn);
                threadPool.execute(handler);
            } catch (Exception e){}
        }
    }

    static class RequestHandler implements Runnable {

        private TTPConnection conn;

        public RequestHandler(TTPConnection conn) {
            this.conn = conn;
        }

        @Override
        public void run() {

            try {

                String path = (String)DataUtil.byteToObject(ttpService.receive(conn));

                File file = new File(path);
                BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
                boolean isFound = file.exists();

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

                int remain = (int)file.length();
                int retVal = 0;
                byte[] buffer = new byte[CHUNK_SIZE];

                while (retVal != -1) {

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


            } catch (FileNotFoundException e){
            } catch (Exception e) {

            }


        }
    }


    private static void printUsage() {
        System.out.println("Usage: java FTPServer <port> <win_size> <timeout>");
        System.exit(-1);
    }
}

