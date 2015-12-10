package com.company;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class TCPServer {

    ServerSocket socket;
    Socket connectedSocket;

    static InetAddress prevClient = null;
    static int receivedPackages = 0;
    static char operation = ' ';
    static String file = "";

    DataInputStream inStream;
    DataOutputStream outStream;

    TCPServer(int port){
        try {
            ServerSocketChannel server = ServerSocketChannel.open();
            socket = server.socket();
            server.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
    int acceptConnection() {
        try {
            System.out.println("Waiting for connection . . .");
            connectedSocket = socket.accept();
            connectedSocket.setSoTimeout(120000);
            connectedSocket.setOOBInline(true);
            //connectedSocket.setReceiveBufferSize(4096);
            //connectedSocket.setSendBufferSize(4096);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return -1;
        }
        System.out.println("Client connected: " + connectedSocket.getInetAddress().toString());

        try {
            inStream = new DataInputStream(connectedSocket.getInputStream());
            outStream = new DataOutputStream(connectedSocket.getOutputStream());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        if(!connectedSocket.getInetAddress().equals(prevClient)){
            receivedPackages = 0;
            operation = ' ';
            file = "";
        }
        prevClient = connectedSocket.getInetAddress();
        try {
            if(operation == ' ')
                outStream.writeChar('0');
            else {
                outStream.writeChar(operation);
                outStream.writeUTF(file);
                if(operation == 'd') {
                    System.out.println("Restore download");
                    receivedPackages = inStream.readInt();
                    if(receivedPackages != 0)
                        fDownloadChannel();
                }
                else {
                    System.out.println("Restore upload");
                    File f = new File("./" + file + ".indownload");
                    if(inStream.readInt() > 0){
                        outStream.writeInt((int)(f.length()/1024));
                        fUploadChannel();
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return 1;
    }
    int run(){
        acceptConnection();
        if( receive() == 0 ) closeConnection();
        return 1;
    }
    int receive(){
        while(!connectedSocket.isClosed()){
            try {
                switch(inStream.readInt()){
                    case 1:                       //echo
                        sendAnswer(readArgs());
                        break;
                    case 2:                       //time
                        sendAnswer(java.util.Calendar.getInstance().getTime().toString());
                        break;
                    case 3:                       //download
                        file = readArgs();
                        if(fDownloadChannel() == -1) return 0;
                        break;
                    case 4:                       //upload
                        file = readArgs();
                        if(fUploadChannel() == -1) return 0;
                        break;
                    case 5:
                        return 0;
                    default: sendAnswer("Command not found");
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
                return 0;
            }
        }
        return 1;
    }
    int fDownloadChannel(){
        operation = 'd';
        System.out.println("Download file " + file + " begins");

        File f = new File("./"  + file);
        int pts = (int)f.length()/1024 - receivedPackages;
        if(f.length() % 1024 != 0) pts++;

        DataInputStream rdFile = null;
        try {
            rdFile = new DataInputStream(new FileInputStream(f));
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
            try {
                operation = ' ';
                outStream.writeInt(0);
            } catch (IOException e1) {
                System.out.println(e.getMessage());
            }
            return 0;
        }
        try {
            rdFile.skip(receivedPackages*1024);
            outStream.flush();
            outStream.writeInt(pts);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        System.out.print("Sended packages:\n0 of " + pts);

        SocketChannel channel = connectedSocket.getChannel();
        Selector selector = null;
        try {
            selector = Selector.open();
            channel.configureBlocking(false);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        SelectionKey selk = null;
        try {
            selk = channel.register(selector, SelectionKey.OP_WRITE);
        } catch (ClosedChannelException e) {
            System.out.println(e.getMessage());
        }

        byte[] buf = new byte[1024];
        int packn = 0;
        int length = 0;
        ByteBuffer bb = ByteBuffer.allocate(1);
        bb.position(1);
        while(packn < pts) {
            if(pts > 100) if (packn % (pts / 100) == 0) {
                selk.cancel();
                try {
                    channel.configureBlocking(true);
                    connectedSocket.sendUrgentData(5);
                    channel.configureBlocking(false);
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
                try {
                    selector = Selector.open();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    selk = channel.register(selector, SelectionKey.OP_WRITE);
                } catch (ClosedChannelException e) {
                    e.printStackTrace();
                }
                System.out.print("\r" + packn + " of " + pts + " [" + ((packn*100/pts)) + "%]");
            }

            int readyChannels = 0;
            try {
                readyChannels = selector.select(20000);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
            if(readyChannels == 0) {
                System.out.println("\nWrite timeout reached");
                selk.cancel();
                try {
                    channel.configureBlocking(true);
                    rdFile.close();
                } catch (IOException e1) {
                    System.out.println(e1.getMessage());
                }
                return -1;
            }



            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isWritable()) {
                    try {
                        if(!bb.hasRemaining()){
                            length = rdFile.read(buf, 0, 1024);
                            bb = ByteBuffer.wrap(buf, 0, length);
                            packn++;
                        }
                        if(packn == pts)
                            while(bb.hasRemaining()) channel.write(bb);
                        else channel.write(bb);
                    } catch (IOException e) {
                        System.out.println(e.getMessage());

                        selk.cancel();
                        try {
                            channel.configureBlocking(true);
                            rdFile.close();
                        } catch (IOException e1) {
                            System.out.println(e1.getMessage());
                        }
                        return -1;
                    }

                }
                keyIterator.remove();
            }
        }

        receivedPackages = 0;
        operation = ' ';
        System.out.println("\r" + pts + " of " + pts + " [100%]");

        try {
            rdFile.close();

            selk.cancel();
            channel.configureBlocking(true);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return 1;
    }
    void fDownload() throws IOException{
        try {
            connectedSocket.setSoTimeout(30000);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        operation = 'd';
        file = "./" + file;
        File f = new File(file);
        DataInputStream rdFile = new DataInputStream(new FileInputStream(f));

        int pts = (int)f.length()/1024 + 1 - receivedPackages;
        rdFile.skip(receivedPackages*1024);
        outStream.flush();
        outStream.writeInt(pts);

        System.out.print("Sended packages:\n0 of " + pts);

        byte[] buf = new byte[1024];
        for(int i = pts; i > 1; i--){
            rdFile.read(buf, 0, 1024);
            outStream.write(buf, 0, 1024);
            if(i % (pts/100) == 0)
                System.out.print("\r" + (pts - i) + " of " + pts + " [" + (((pts - i)* 100)/pts) + "%]");
        }

        if(inStream.readBoolean() == true)
            operation = ' ';
        outStream.writeInt((int)(f.length() - (f.length()/1024)*1024));
        rdFile.read(buf, 0, (int)(f.length() - (f.length()/1024)*1024));
        outStream.write(buf, 0, (int)(f.length() - (f.length()/1024)*1024));
        System.out.println("\r" + pts + " of " + pts + " [100%]");
        rdFile.close();

        try {
            connectedSocket.setSoTimeout(120000);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
    int fUploadChannel(){
        operation = 'u';
        System.out.println("Upload file " + file + " begins");

        FileWriter out = null;
        try {
            out = new FileWriter("./" + file + ".indownload", true);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        BufferedWriter wrToFile = new BufferedWriter(out);

        int packagesToReceive = 0;
        try {
            packagesToReceive = inStream.readInt();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        if(packagesToReceive == 0) return 1;
        System.out.print("Received packages:\n0 of " + packagesToReceive);

        SocketChannel channel = connectedSocket.getChannel();
        Selector selector = null;
        try {
            selector = Selector.open();
            channel.configureBlocking(false);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        SelectionKey selk = null;
        try {
            selk = channel.register(selector, SelectionKey.OP_READ);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        }

        ByteBuffer buf = ByteBuffer.allocate(1024);
        int packn = 0;
        while(packn < packagesToReceive) {
            int readyChannels = 0;
            try {
                readyChannels = selector.select(20000);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
            if(readyChannels == 0) {
                System.out.println("Read timeout reached");
                selk.cancel();
                try {
                    channel.configureBlocking(true);
                    wrToFile.close();
                } catch (IOException e1) {
                    System.out.println(e1.getMessage());
                }
                return -1;
            }

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isReadable()) {
                    int length = 0;
                    try {
                        length = channel.read(buf);
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                        selk.cancel();
                        try {
                            channel.configureBlocking(true);
                            wrToFile.close();
                        } catch (IOException e1) {
                            System.out.println(e1.getMessage());
                        }
                        return -1;
                    }
                    if(packn == packagesToReceive - 1){
                        if(length == 0) packn++;
                        else{
                            try {
                                wrToFile.append(new String(buf.array()), 0, length);
                            } catch (IOException e) {
                                System.out.println(e.getMessage());
                            }
                            packn++;
                        }
                    }
                    else if(!buf.hasRemaining()) {
                        packn++;
                        try {
                            wrToFile.append(new String(buf.array()), 0, 1024);
                        } catch (IOException e) {
                            System.out.println(e.getMessage());
                        }
                        if(packagesToReceive > 100) if(packn % (packagesToReceive/100) == 0)
                            System.out.print("\r" + packn + " of " + packagesToReceive + " [" + ((packn* 100)/packagesToReceive) + "%]");
                    }
                    buf.clear();
                }
                keyIterator.remove();
            }
        }

        receivedPackages = 0;
        operation = ' ';
        System.out.println("\r" + packagesToReceive + " of " + packagesToReceive + " [100%]");
        try {
            wrToFile.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        File f = new File(file + ".indownload");
        f.renameTo(new File(file));

        selk.cancel();
        try {
            channel.configureBlocking(true);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return 1;
    }
    String readArgs() throws IOException {
        String s = "";
        do{
            s += inStream.readUTF();
        }while(s.charAt(s.length() - 1) != '\0');
        s = s.substring(0, s.length() - 1);
        return s;
    }

    void sendAnswer(String s){
        try {
            outStream.writeUTF(s + '\0');
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
    void closeConnection(){
        try {
            System.out.println("Socket closed");
            connectedSocket.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}