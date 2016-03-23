package applications;

import datatypes.FTPData;
import datatypes.FTPMeta;
import services.DataUtil;
import services.TTPConnection;
import services.TTPService;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FTPClient {

    public static void main(String[] args) throws ClassNotFoundException, IOException{

        if(args.length != 4) {
            printUsage();
        }
        System.out.println("Starting FTPClient ...");

        int port = Integer.parseInt(args[0]);
        int winSize = Integer.parseInt(args[1]);
        int timeout = Integer.parseInt(args[2]);
        String path = args[3];

        TTPService ttpService = new TTPService(winSize, timeout, port);
        TTPConnection conn = ttpService.connect("127.0.0.1", (short) 65531, "127.0.0.1", (short) 65530, 10);


        boolean isValid = false;
        while (!isValid) {

            ttpService.send(conn, DataUtil.objectToByte(path));
            System.out.println("requesting file: " + path);
            FTPMeta meta = (FTPMeta) DataUtil.byteToObject(ttpService.receive(conn));

            // loop to receive all the data
            int size = meta.getTotalSize();
            int offset = 0;
            while(offset < size) {
                FTPData data = (FTPData) DataUtil.byteToObject(ttpService.receive(conn));
                writeContent(path, data.getData(), offset, data.getSize());
                offset += data.getSize();
            }

            // validate MD5Checksum
            isValid = isMD5Valid(path, meta.getMd5Checksum());
            if (!isValid) {
                Files.delete(Paths.get(path));
            }
        }

        ttpService.close(conn);
    }

    public static boolean isMD5Valid(String path, String expected) {
        try {
            String checksum = DataUtil.getMD5Checksum(path);

            System.out.println("Received: " + checksum);
            System.out.println("Expected: " + expected);

            return checksum.equals(expected);

        } catch (FileNotFoundException e) {
            return false;
        }
    }


    public static void writeContent(String path, byte[] content, int off, int length) throws IOException {

        // need to use seek, so switch to RandomAccessFile
        RandomAccessFile fileStream = new RandomAccessFile(path,"rwd");
        fileStream.seek(off);
        fileStream.write(content, 0, length);
        fileStream.close();
    }

    private static void printUsage() {
        System.out.println("Usage: java FTPClient <port> <win_size> <timeout> <file_path>");
        System.exit(-1);
    }

}
