
package c650luserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ListIterator;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class C650LuServer {
   
    public static void main(String[] args) throws InterruptedException {   
  
        //Define the port number to listen on
        int portNumber = 8080;  //Change this to 80 before submitting!
        
        LHSServer lhsServer = new LHSServer();
        
        //Determine how many IP addresses there and how many threads to run
        //NOTE: Add Run path for working directory in Netbeans project properties to the path listed ../text/ip.txt
        String path = "../text/ip.txt"; //This path should change to the same directory as the jar before submitting!
        File file = new File(path);
        int count = 0;
        try {
            FileReader fileReader = new FileReader(file);
            BufferedReader reader = new BufferedReader(fileReader);
            
            String text = null;
            while ((text = reader.readLine()) != null) {
                if (!(text.equalsIgnoreCase("")))
                    count++;
            }
            //System.out.println("COUNT: " + count);
        } catch (IOException e) {
            System.out.println("IO Error: " + e.getMessage());
        } 
        
        lhsServer.threadNumber = count;//Set global var of number of threads
        //System.out.println("threadNumber = " + lhsServer.threadNumber);
        
        //*** Try to now set array length
        lhsServer.ewsIPAddresses = new String[lhsServer.threadNumber];

    
        //Call to get timeout value from user
        TimeSet myTimer = new TimeSet();
        lhsServer.timerValue = myTimer.setTimeout();
        //System.out.println("myTimer val = " + timerValue);    //Trace

        //Call to set and run timer
        lhsServer.setTimer(lhsServer.timerValue);
        
        try (                
                ServerSocket serverSocket = new ServerSocket(portNumber);
                Socket clientSocket = serverSocket.accept();
                PrintWriter out =
                    new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            ) {
                String inputLine, outputLine;
                outputLine = "HTTP/1.1 404 Not Found\r\n\r\n";
 
                //Receive GET request from browser
                while ((inputLine = in.readLine()) != null) {
                    lhsServer.GET_Request.add(inputLine);

                    if (inputLine.isEmpty()) {
                        //Send HTTP 404 to client browser
                        out.println(outputLine);
                        serverSocket.close();
                        clientSocket.close();
                        break;
                    }
                }
                
                /* Test for host line number, NOTE: we just need to find this out 
                *  in main thread, index will be the same foe ALL threads 
                */
                ListIterator i = lhsServer.GET_Request.listIterator();
                while (i.hasNext()) {
                    if (i.next().toString().startsWith("Host: ")) {
                        lhsServer.index = i.previousIndex();
                        lhsServer.ipAddress = lhsServer.GET_Request.elementAt(lhsServer.index);
                    }
                }

        } catch (IOException e) {
            System.out.println("Exception caught listening on port");
            System.out.println(e.getMessage());
        }
      
        //Print original GET request
        System.out.println("\nThe original GET Request from client browser:");
        ListIterator j = lhsServer.GET_Request.listIterator();
        while (j.hasNext()) {
            System.out.println(j.next());
        }
        //Create threads
        for (int i = 0; i < lhsServer.threadNumber; i++) {
           LHSThread lhsThread = new LHSThread();
           lhsThread.thread_ID = i;
           lhsThread.GET_Request = (Vector) lhsServer.GET_Request.clone();
           lhsThread.start();
           lhsThread.join();//Wait for each thread to finish
           //System.out.println("ewsIPAddress" + i + " = " + lhsServer.ewsIPAddresses[i]);//Trace test
           System.out.println("Thread" + lhsThread.thread_ID + " is done");//Trace
        }
        //***************  All Threads Are Done at This Point  *************
        
        //Create new class to read EWS Response file
        //Read EWS Response file, print it to screen, and send it UDP to Client
        //lhsServer.getEWSFile();
        
        EWSResponse myResponse = new EWSResponse();
        
        //Test if the EWS response exists, this calls to send the file UDP as well
        //*********** turn off for testing if you want to keep it from printing to screen ***************/
        myResponse.testForFile();
     
        //Writes EWS Response, this function has been added into 
        //myResponse.printEWSResponse(myResponse.fileName);
    }
}

class EWSResponse extends LHSServer implements Serializable {
    int number;
    String[] fileName = new String[lhsServer.threadNumber];
    double[] fileSize = new double[lhsServer.threadNumber];
    double[] numberOfPackets = new double[lhsServer.threadNumber];
    String[] queue = new String[lhsServer.threadNumber];
    static boolean empty;//Set based on if queue is empty or not
    
    //Opens socket, prints it to screen, receives response from client
    public void sendFileUDP(EWSResponse ewsResponse) { 
        
        DatagramSocket datagramSocket = null;
        BufferedReader bufferedReader = null;
        
        //Loop to read fileNames in queue of done files
        for (int i = 0; i < lhsServer.threadNumber; i++) {
            System.out.println("---------------------------------------------");//Trace
   
            //Get the next fileName in queue
            String nameOfFile = ewsResponse.queue[i];
            System.out.println("nameOfFile: " + nameOfFile);//Trace
            //Get Ip address
            String ipNumber = lhsServer.ewsIPAddresses[i];
            System.out.println("UDP IpNumber: " + ipNumber);//Trace
            //Get number of packets
            double packetsToSend = ewsResponse.numberOfPackets[i];
            System.out.println("UDP packetsToSend: " + packetsToSend);
            //Get the file size
            double sizeOfFile = ewsResponse.fileSize[i];
            System.out.println("UDP INFO Size: " + sizeOfFile + " bytes");//Trace
                
            /*** RESET TIMER HERE for each file to send ***/
            try {
                this.resetTimer(lhsServer.timerValue, ipNumber, i);
                System.out.println("Timer Reset after " + nameOfFile);//Trace
            } catch (Exception e) {
                System.out.println("Timer reset error: " + e.getMessage());
            }
            
            int portNumber = 13671; 
            
            boolean ack = true;//For client ACK

                try {
                    int buffer = 1024;
                    datagramSocket = new DatagramSocket();
                    byte[] ackbuf = new byte[1024];
                    byte[] infobuf = new byte[1024];
                    byte[] buf = new byte[1024];
                    System.out.println("1 buf Length: " + buf.length);//Trace
 
                    //Assign line to buf
                    String line = ipNumber + " " + packetsToSend + " " + sizeOfFile;
                    System.out.println("UDP INFO packet to send: " + line);//Trace test
                    infobuf = line.getBytes();
                    
                    //Send info packet to client
                    InetAddress address = InetAddress.getByName("localhost");
                    DatagramPacket packet = new DatagramPacket(infobuf, infobuf.length, address, portNumber);
                    datagramSocket.send(packet);
                    
                    //Get file and send it
                    File file = new File(nameOfFile);
                    bufferedReader = new BufferedReader(new FileReader(nameOfFile));
                    FileInputStream fileInStream = new FileInputStream(file);                 
                    
                    int countIndex = 0;//trace
                    int byteCount = 0;
                    int packetCount = 0;
                    int offset = 0;
                    int lastPacketSize = (int)((packetsToSend * 1024) - sizeOfFile);
                    
                    while((byteCount = fileInStream.read(buf)) != -1) {                        
                        System.out.println("buf Length: " + buf.length);//Trace
                        System.out.println("++++++++++++++++++++++++++++++++++++");
                        offset = packetCount * 1024;
                        
                        System.out.println("countIndex = " + countIndex + "  offSet = " + offset +
                                "   bytecount = " + byteCount + " lastPacketSize = " + lastPacketSize);
                        if (packetsToSend <= 0)
                            break;
                        
                        packet = new DatagramPacket(buf, buf.length, address, portNumber);
                        if ( packetsToSend == 1 ) {
                            System.out.println("\n\n^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^\n");
                            packet = new DatagramPacket(buf, 0, byteCount, address, portNumber);
                            String packetLine = packet.getData().toString();//trace
                            System.out.println("From +-+ Server: Packet " + packetCount + "  " + (new String(buf)));//trace
                            System.out.println("packet length: " + packet.getLength() + "  Offset: " + packet.getOffset());
                        }
                        else {
                            packet = new DatagramPacket(buf, buf.length, address, portNumber);
                            String packetLine = packet.getData().toString();//trace
                            System.out.println("From Server: Packet " + packetCount + "  " + (new String(buf)));//trace
                            System.out.println("packet length: " + packet.getLength());
                        }
                        
                        datagramSocket.send(packet);
                        packetsToSend--;
                        packetCount++;
                        countIndex++;
                    }
                 
                     System.out.println("\n\n##################################" +
                            "####################################\n\n");//Trace
                     
                    //Receive packet from client, NOTE: this will stop exec and wait for a packet to arrive
                    DatagramPacket recPacket = new DatagramPacket(ackbuf, ackbuf.length);
                    datagramSocket.receive(recPacket);
                    ackbuf = recPacket.getData();
                    
                    /*** This needs to be implemented when Client program is done ***/
                    //Test for ACK from client
                    String inline = new String(recPacket.getData());
                    System.out.println("From Client: " + inline);
                    if (inline.contains("OK")) {
                        System.out.println("Server Receives: OK from EWS IP: " + ipNumber);
                        continue;
                    }
                    else if (inline.contains("Fail")) {
                        System.out.println("Server Receives: FAIL from EWS IP: " + ipNumber);
                        ack = false;
                        continue;
                    }
                    
                    else {
                        //Wait for timer to expire
                        System.out.println("OTHER FAIL");//Needed???
                        
                    }
                    
                    System.out.println("\n\n##################################" +
                            "####################################\n\n");//Trace
//
                } catch (IOException e) {
                    System.out.println("UDP Socket error: "+ e.getMessage()) ;
                    e.printStackTrace();
                    //break;
                } 

                packetsToSend--;
        }
        //When all done reading files to send to Client, close connection
        datagramSocket.close();
    }
    
    //This method calls method sendFileUDP above 
    public void testForFile() {
        EWSResponse ewsResponse = this;
        boolean moreFiles = true;
        
        int i = 1;
        int flag = 0; 
        while (moreFiles) {
            String ewsFile = "../dist/" + "c650Lu" + i + ".txt";
            File file = new File(ewsFile);
            if (file.exists()) {
                ewsResponse.number = i;//To identify the ewsResponse
                ewsResponse.fileName[flag] = ewsFile;
                System.out.println("Filename = " + ewsFile);//Trace test
                
                //Add completed files to queue to be sent via UDP
                ewsResponse.queue[flag] = ewsResponse.fileName[flag];
                System.out.println("Queue " + flag + " filename = " + ewsResponse.queue[flag] + " timestamp: " + file.lastModified());//Trace
                
                //Get the file size in bytes
                double bytes = file.length();
                ewsResponse.fileSize[flag] = bytes;
                System.out.println("FILE SIZE: " + bytes + " bytes");//Trace

                //Number of packets to send
                double totalPackets = bytes/1024;
                System.out.println("Number of packets: " + totalPackets);//Trace
                ewsResponse.numberOfPackets[flag] = Math.ceil(bytes/1024);
                System.out.println("Number of packets rounded up: " + numberOfPackets[flag]);//Trace
                
                flag++;
            }
            if (flag >= 3) {
                System.out.println("\nNo More files to Read");
                moreFiles = false; 
            }
            i++;
        }
        
        //Call to method to open UDP socket with LHC
        sendFileUDP(ewsResponse);
    } 
    
    //Is this even needed, or can it be done during sendFileUDP???
    public void printEWSResponse(String responseFile) { 
        String line = null;
        //Open and read the file
        BufferedReader bufferedReader = null;
            
        File file = new File(responseFile);
        if (file.exists()) {
            try { 
                bufferedReader = new BufferedReader(new FileReader(responseFile));
                
                //Write out the file contents to screen
                while ((line = bufferedReader.readLine()) != null) {
                    System.out.println("$ " + line);
                }
            } catch (IOException e) {
                System.out.println("IO Error reading EWS response file: " + e.getMessage());
            } finally {
                try {
                    if (bufferedReader != null) bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }    
    }
}

class LHSServer extends Thread {
    String ipAddress;//Raw host line
    int thread_ID;
    static int index; //Store index of Vector where ip is
    static LHSServer lhsServer;
    static int threadNumber;
//    static String[] ewsIPAddresses = new String[lhsServer.threadNumber];
    static String[] ewsIPAddresses;

    Vector<String> GET_Request = new Vector<>(); 
    Vector<String> EWS_Response = new Vector<>(); 
    Vector<String> EWS_ResponseFileName = new Vector<>();
    static Timer myTimer;
    static int timerValue;

    //Sets original timer value
    public void setTimer(int timerValue) {
        LHSServer.myTimer = new Timer();
        LHSServer.myTimer.schedule(new TimerTask() {

          @Override
          public void run() {
              timeExpired();
              System.exit(1);
            }
        },timerValue);
    }
    
    //Reset timer value, cancels original timer
    public void resetTimer(int timerValue, final String ipNumber, final int counter) {
        LHSServer.myTimer.cancel();//cancel the old timer
        LHSServer.myTimer.purge();//and purge it just to be sure
        
        LHSServer.myTimer = new Timer();
        LHSServer.myTimer.schedule(new TimerTask() {

          @Override
          public void run() {
              System.out.println("\t\tNew Timer Running ");//Trace test
              System.out.println("FAIL for EWS IP: " + ipNumber + " counter = " + counter);//Trace test
              if (counter == lhsServer.threadNumber-1) {
                  //System.out.println("FAIL for EWS IP: " + ipNumber + " counter = " + counter);//Trace test
                  timeExpired();
                  System.exit(1);
              } 
            }
        },timerValue);
    }
    
    public boolean timeExpired() {
        System.out.println("Time has expired! Program ending.");
        return false;
    } 

    public void getEWSFile() {
        int i = 1;
        while (i <= lhsServer.threadNumber) {
            String fileName = "../dist/" + "c650Lu" + i + ".txt";
            System.out.println("File name:  " + fileName);//Trace
            String line = "Test";
            //Open and read the file
            BufferedReader bufferedReader = null;
            File file = new File(fileName);
            try { 

                bufferedReader = new BufferedReader(new FileReader(fileName));
                //Write out the file contents to screen
                while ((line = bufferedReader.readLine()) != null) {
                    System.out.println("$ " + line);                    
                }
            } catch (IOException e) {
                System.out.println("IO Error reading EWS response file: " + e.getMessage());
            } finally {
                try {
                    if (bufferedReader != null) bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //Get the file size in bytes
            double bytes = file.length();
            System.out.println("FILE SIZE: " + bytes + " bytes");//Trace

            //Number of packets to send
            double totalPackets = bytes/1024;
            System.out.println("Number of packets: " + totalPackets);//Trace
            double numberOfPackets = Math.ceil(bytes/1024);
            System.out.println("Number of packets rounded up: " + numberOfPackets);//Trace
            
            //Open a UDP socket on port 13671
            DatagramSocket datagramSocket = null;//client socket
            int portNumber = 13671;
            
            while(numberOfPackets > 0) {
                System.out.println("Packets sent from " + fileName);
                System.out.println("line to buf = " + line);
                
                try {
                    datagramSocket = new DatagramSocket(portNumber);
                    byte[] buf = new byte[1024];
                    int j = 0;

                    //Receive from client
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    datagramSocket.receive(packet);
                    
                    //Prepare response for client
                    buf = line.getBytes();

                    //Send response to client
                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    packet = new DatagramPacket(buf, buf.length, address, port);
                    datagramSocket.send(packet);

                } catch (IOException e) {
                    System.out.println("UDP Socket error: "+ e.getMessage()) ;
                    //break;
                }
                numberOfPackets--;
            }
            datagramSocket.close();
            
            i++;
        }
    }
}

class LHSThread extends LHSServer {
    //run method below is where execution for threads starts
    public void run() {
                
        //Call to read text file and switch IPs with path to text file 
        String newIpAddress = this.txtFileReader();
        
        //Call to swap ip's in GET request
        this.injectEWSAddress("Host: " + newIpAddress);
        
        System.out.println("\nThe Modified GET Request (From Thread " + this.thread_ID + "): ");
        this.printGETRequest();
        
        //Open new socket for thread save to EWS_Response
        this.clientSocket();
        
        //Trace Test to see what EWS-Response has written to it, off unless testing
        //printEWSResponse(this);
                
        //Call to write file from EWS
        this.writeFile();
        
        //Print IP address of EWS and "Done" message
        this.printDone();
            
        //Read EWS Response file, print it to screen, and send it UDP to Client
        //this.getEWSFile();
    }
    
    public void udpServer(String line) throws IOException {
        DatagramSocket socket = new DatagramSocket(51150);
        while (true) {
            try {
                byte[] buf = new byte[1024];
 
                // receive request
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
 
                // figure out response
                String dString = line;
                if (line == null) {
                    System.out.println("UDP DONE!");
                    break;
                }
 
                buf = dString.getBytes();
 
                // send the response to the client at "address" and "port"
                InetAddress address = packet.getAddress();
                int port = packet.getPort();
                packet = new DatagramPacket(buf, buf.length, address, port);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        socket.close();    
    }
   
    public void printDone() {
        System.out.println("\nEWS IP Address: " + this.ipAddress + "  DONE");
    }
    
    public void writeFile() { 
        PrintWriter printWriter = null;
        try {
            String fileName = setFileName();//Call method to set the filename
            File outfile = new File(fileName); 

            printWriter = new PrintWriter(outfile);
            ListIterator j = this.EWS_Response.listIterator();
            while (j.hasNext()) {
                String line = (String) j.next();//Trace here
                
                printWriter.println(line);//Need this
                
                //To add EWS IP from each thread to static array
                lhsServer.ewsIPAddresses[this.thread_ID] = this.ipAddress;//Use the threadID
            } 
        } catch (Exception e) {
            System.out.println("WriteFile 1 Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                printWriter.close();
            } catch (Exception e) {
                System.out.println("WriteFile 2 Error: " + e.getMessage());
            }
        }
    }
        
    public String setFileName() {
        String fileName = "c650Lu" + (this.thread_ID+1) + ".txt";
        
        //Grab this name and store it for later retrieval
        this.EWS_ResponseFileName.add(fileName);

        return fileName;
    }
           
    public void clientSocket() {
        String hostName = this.ipAddress;
        int portNumber = 80;
 
        try (
            Socket ewsSocket = new Socket(hostName, portNumber);
            PrintWriter out = new PrintWriter(ewsSocket.getOutputStream(), true);
            InputStreamReader isr = new InputStreamReader(ewsSocket.getInputStream(), "UTF-8");//convert encoding
            BufferedReader in = new BufferedReader(isr);        
        ) {
            String fromServer = null;
                
            //Send GET request to EWS
            ListIterator k = this.GET_Request.listIterator();
            while (k.hasNext()) {
                String line = (String) k.next();//Trace test
                    out.println(line);
            }
            out.println("\r\n\r\n");//To indicate end of GET request to EWS
              
            //Testing for length response
            byte[] buffer = new byte[10 * 1024];
            int totalLength = 0;
            int headerLength = 0;
            int currentLength = 0;
            int contentLength = 0;
            int bytesToRead = 0;
            boolean flag = true;
            boolean headerDone = false;
            String lengthString = null;
            int charCount = 0;
            
            /////Char count vars needed
            boolean clFlag = true;//Content-length flag
            int clStart = 0;//Content-length start
            int clEnd = 0;//Content-length end
            int i = 0;//char value read in
            StringBuilder sb = new StringBuilder();
            
            String line = null;
            while ((i = in.read()) != -1 && flag) {
                charCount++;
                sb.append((char)i);
                line = sb.toString();
                buffer = line.getBytes();
                totalLength = charCount;
                currentLength = totalLength;
                
                //Test to get Content length
                if (line.contains("Content-Length: ")&& clEnd == 0) {
                    if (clFlag) {
                        clStart = charCount;
                        clFlag = false;
                    }
                    
                    if ((char)i == '\r' || (char)i == '\n') {
                        clEnd = charCount;
                        String num = sb.substring(clStart, clEnd-1);
                        contentLength = Integer.parseInt(num);                        
                    }
                }
                
                //Test for end of header
                if ((line.contains("\r\n\r\n")&& headerDone == false)) {  //Look for two CRLF to signal end of header
                    headerLength = currentLength; 
                    bytesToRead = contentLength + headerLength;
                    headerDone = true;
                }
                
                if (headerDone) {
                    if((bytesToRead <= currentLength-1) || ((line.contains("</html>") && headerDone == true))) {
                        flag = false;
                        break;
                    }
                        
                }
            }
            this.EWS_Response.add(line);//Write to text file
            //System.out.println("(((((((((((((((  BREAK !!!   ))))))))))))))))");//trace
            ewsSocket.close();
            
        } catch (UnknownHostException e) {
            System.err.println("Can't find host on " + hostName);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("I/O Error on " +
                this.ipAddress + e.getMessage());
            System.exit(1);
        }
    }
    
    public void printGETRequest() {
        ListIterator k = this.GET_Request.listIterator();
        while (k.hasNext()) {
            System.out.println(k.next());
        }
    } 
    
    public void printEWSResponse(LHSThread myThread) {
        ListIterator k = myThread.EWS_Response.listIterator();
        while (k.hasNext()) {
            System.out.println(k.next());
        }
    } 
    
    public String sendGETRequest() {
    ListIterator k = this.GET_Request.listIterator();
    while (k.hasNext()) {
        String line;
        line = (String) k.next();
        System.out.println("LINE = " + line);
        return line;
    }
    return "sendGetRequest Error";
    } 
    
    public String txtFileReader() { 
        try {
            String path = "../text/ip.txt"; //add Run path for working directory Place in same dir for FINAL
            File file = new File(path);
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            int count = lhsServer.threadNumber;//Use variable set from lines of text
            String[] ipTextAddress = new String[count];
            for (int i = 0; i < count; i++) {
                ipTextAddress[i] = bufferedReader.readLine();
                if (this.thread_ID == i) {
                    this.ipAddress = ipTextAddress[i];
                    return this.ipAddress;
                }
            }
        } catch (IOException e) {
            System.out.println("IO Error: " + e.getMessage());
        }       
        return "Host address not found!!!";
    }
       
    public void injectEWSAddress(String ip) {
        this.GET_Request.setElementAt(ip, this.index);
    }
}

class TimeSet {
    int timeout_value;
    public int setTimeout() {
        System.out.println("Please enter a timeout value in ms for the LHS and press enter: ");
        Scanner reader = new Scanner(System.in);
        timeout_value = reader.nextInt();
        System.out.println("You entered a timeout value of " + timeout_value + "ms");
        return timeout_value;
    }
}




