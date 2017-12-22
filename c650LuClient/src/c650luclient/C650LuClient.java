/**
 * This file is just for testing the Server program
 * 
 */

package c650luclient;

import java.io.*;
import java.net.*;

public class C650LuClient {

    public static void main(String[] args) throws IOException {
        System.out.println("Client running ... ");   
        // get a datagram socket
        int portNumber = 13671;
 
        byte[] buf = new byte[1024];
        InetAddress address = InetAddress.getByName("localhost");
        DatagramSocket socket = new DatagramSocket(portNumber);

        DatagramPacket packet = null;
        
        // display response
        int packetsReceived = 0;
        boolean flag = true;
        while (flag) {
            packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            
            String received = new String(packet.getData(), 0, packet.getLength());
            System.out.println("From Server: " + received);
            packetsReceived++;
            if (received.equalsIgnoreCase("</html>") || packetsReceived > 34)//Just to force it to stop
                flag = false;
        }
        String ok = "OK 136.160.171.110 35.0 35542.0";
        String fail = "Fail";
        int port = packet.getPort();
        buf = ok.getBytes();
        packet = new DatagramPacket(buf, buf.length, address, port);
        
        socket.send(packet);
        System.out.println("Response sent");
        socket.close();
    }
    
}
