package com.example.worker01;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Scanner;

@Slf4j
public class LongConnTest {

    private Logger logger = LoggerFactory.getLogger(LongConnTest.class);

    String host = "192.168.3.209";
    int port = 80;

    public void testLongConn() throws Exception {
        logger.debug("start");
        final Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port));
        Scanner scanner = new Scanner(System.in);
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        new Thread(() -> {
            while (true) {
                try {
                    byte[] input = new byte[64];
                    int readByte = socket.getInputStream().read(input);
                    logger.debug("readByte " + readByte);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        int code;
        while (true) {
            code = scanner.nextInt();
//            code = 1;
            logger.debug("input code:" + code);
            if (code == 0) {
                break;
            } else if (code == 1) {

                ByteBuffer byteBuffer = ByteBuffer.allocate(5);
                byteBuffer.put((byte) 1);
                byteBuffer.putInt(0);
                bufferedWriter.write("GET /heart HTTP/1.1\r\n");
                bufferedWriter.write("Host: restapi.amap.com\r\n\r\n");
                bufferedWriter.flush();
                logger.debug("write heart finish!");
            } else if (code == 2) {
                byte[] content = ("hello, I'm" + hashCode()).getBytes();
                ByteBuffer byteBuffer = ByteBuffer.allocate(content.length + 5);
                byteBuffer.put((byte) 2);
                byteBuffer.putInt(content.length);
                byteBuffer.put(content);
                bufferedWriter.write("GET /wangrui HTTP/1.1\r\n");
                bufferedWriter.write("Host: restapi.amap.com\r\n\r\n");
                bufferedWriter.flush();
                logger.debug("write content finish!");
            }
        }
        System.out.println("close");
        socket.close();
    }

    // 因为Junit不支持用户输入,所以用main的方式来执行用例
    public static void main(String[] args) throws Exception {
        new LongConnTest().testLongConn();
    }
}