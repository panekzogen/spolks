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

    TCPServer(){
        try {
            ServerSocketChannel server = ServerSocketChannel.open();
            socket = server.socket();
            server.bind(new InetSocketAddress(6789));
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
    int acceptConnection() {
        try {
            System.out.println("Waiting for connection . . .");
            connectedSocket = socket.accept();
            connectedSocket.setSoTimeout(120000);
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
                sendAnswer(file);
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
        String[] command;
        while(!connectedSocket.isClosed()){
            try {
                command = readCommand().split(" ", 2);
            } catch (IOException e) {
                System.out.println(e.getMessage());
                return 0;
            }
            switch(command[0].toLowerCase()){
                case "echo":
                    sendAnswer(command[1]);
                    break;
                case "time":
                    sendAnswer(java.util.Calendar.getInstance().getTime().toString());
                    break;
                case "download":
                    System.out.println("File download start");
                    file = command[1];
                    fDownloadChannel();
                    break;
                case "upload":
                    System.out.println("File upload start");
                    file = command[1];
                    fUploadChannel();
                    break;
                case "exit":case "quit":case "close":
                    return 0;
                default: sendAnswer("Command not found");
            }
        }
        return 1;
    }
    void fDownloadChannel(){
        operation = 'd';
        file = "./" + file;
        File f = new File(file);
        int pts = (int)f.length()/1024 + 1 - receivedPackages;

        DataInputStream rdFile = null;
        try {
            rdFile = new DataInputStream(new FileInputStream(f));
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
            return;
        }
        try {
            rdFile.skip(receivedPackages*1024);
            outStream.flush();
            outStream.writeInt(pts);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.print("Sended packages:\n0 of " + pts);


        SocketChannel channel = connectedSocket.getChannel();
        Selector selector = null;
        try {
            selector = Selector.open();
            channel.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        SelectionKey selk = null;
        try {
            selk = channel.register(selector, SelectionKey.OP_WRITE);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        }

        byte[] buf = new byte[1024];
        int packn = 0;
        while(packn < pts) {
            int readyChannels = 0;
            try {
                readyChannels = selector.select(20000);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(readyChannels == 0) break;

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isWritable()) {
                    int length = 0;
                    try {
                        length = rdFile.read(buf, 0, 1024);
                        ByteBuffer bb = ByteBuffer.wrap(buf, 0, length);
                        if (channel.write(bb) != 0) packn++;
                    } catch (IOException e) {
                        e.printStackTrace();

                        selk.cancel();
                        try {
                            channel.configureBlocking(true);
                            rdFile.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        return;
                    }
                    if(pts > 100) if (packn % (pts/100) == 0)
                        System.out.print("\r" + packn + " of " + pts + " [" + ((packn*100/pts)) + "%]");

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
            e.printStackTrace();
        }

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
    void fUploadChannel(){
        operation = 'u';
        System.out.println("Download file " + file);
        FileWriter out = null;
        try {
            out = new FileWriter("./" + file + ".indownload", true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedWriter wrToFile = new BufferedWriter(out);

        int packagesToReceive = 0;
        try {
            packagesToReceive = inStream.readInt();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.print("Received packages:\n0 of " + packagesToReceive);

        SocketChannel channel = connectedSocket.getChannel();
        Selector selector = null;
        try {
            selector = Selector.open();
            channel.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
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
                e.printStackTrace();
            }
            if(readyChannels == 0) break;

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isReadable()) {
                    int length = 0;
                    try {
                        length = channel.read(buf);
                    } catch (IOException e) {
                        e.printStackTrace();
                        selk.cancel();
                        try {
                            channel.configureBlocking(true);
                            wrToFile.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        return;
                    }
                    if(length != 0) {
                        packn++;
                        try {
                            wrToFile.append(new String(buf.array()), 0, length);
                        } catch (IOException e) {
                            e.printStackTrace();
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
            e.printStackTrace();
        }

        File f = new File(file + ".indownload");
        f.renameTo(new File(file));

        selk.cancel();
        try {
            channel.configureBlocking(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    String readCommand() throws IOException {
        String s = null;
        s = inStream.readUTF();
        return s;
    }

    void sendAnswer(String s){
        try {
            outStream.writeUTF(s);
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
