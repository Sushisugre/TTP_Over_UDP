package services;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Util that handles data object to be feed in the transport channel
 */
public class DataUtil {

    /**
     * Serialize data object to byte array
     * @param object data object
     * @return byte array
     */
    public static byte[] objectToByte(Object object) {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
            objOut.writeObject(object);
            return byteOut.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return byteOut.toByteArray();
        }
    }

    public static Object byteToObject(byte[] data) {

        ByteArrayInputStream byteIn = new ByteArrayInputStream(data);
        Object obj = null;
        try {
            ObjectInputStream objIn = new ObjectInputStream(byteIn);
            obj = objIn.readObject();
        }catch (IOException e){
        }catch (ClassNotFoundException e) {
        }
        return obj;
    }


    /***
     * Calculate checksum use the UDP approach
     * @return checksum
     */
    public static short getUDPCheckSum(byte[] data){
        int length = data.length;
        int sum = 0;

        // divide data into 16 bits words, add then together
        for (int i = 0; i<length; i = i+2){
            int word = ((data[i]<<8) & 0xFF00);
            if( i+1 < length ) word += (data[i+1] & 0xFF);
            sum += word;

            // carry bit wrap around
            // careful that adding carry bit itself may result in new carry bit
            while ((sum>>16) == 1)
                sum = (sum & 0xFFFF) + 1;
        }

        sum = ~sum;
        sum = sum & 0xFFFF;
        return (short) sum;

    }

    public static String getMD5Checksum(String path) throws FileNotFoundException{

        byte[] buffer = new byte[1024];

        InputStream fis =  new FileInputStream(path);
        MessageDigest messageDigest = null;
        byte[] digest = null;

        try{
            messageDigest = MessageDigest.getInstance("MD5");
        }catch (NoSuchAlgorithmException e) {
            return null;
        }

        try{

            int retVal = 0;
            while (retVal != -1) {
                retVal = fis.read(buffer);
                if (retVal > 0) {
                    messageDigest.update(buffer, 0, retVal);
                }
            }

            fis.close();
            digest = messageDigest.digest();

        } catch (IOException e) {}

        return new String(digest);
    }
}
