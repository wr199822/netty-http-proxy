package com.example.worker01.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author wangrui
 * @date 2022/12/31
 * @Description
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class HttpProxyServerHandler extends ChannelInboundHandlerAdapter {

    @Value("${netty.target-ip}")
    private String target_ip;

    @Value("${netty.target-port}")
    private String target_port;

    @Value("${netty.rewrite-host}")
    private String rewrite_host;


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        if (msg instanceof HttpRequest) {
            //把请求转发到target_ip:target_port 然后转发请求 拿到response 然后在转发到客户端
            DefaultHttpRequest request = (DefaultHttpRequest) msg;
            String uri = request.uri();
            if ("/favicon.ico".equals(uri)) {
                return;
            }
            log.info(uri);
            //将url拼接到targetip:target_port中
            try {
                System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
                URL url = new URL("http://"+target_ip+":"+target_port + uri);
                log.info(url.toString());
                URLConnection urlConnection = url.openConnection();
                HttpURLConnection connection = (HttpURLConnection) urlConnection;
                //根据不同method 处理不同的请求 把参数也带上
                connection.setRequestMethod(String.valueOf(request.method()));
                HttpHeaders headers = request.headers();
                for (Map.Entry<String, String> header : headers) {
                        connection.setRequestProperty(header.getKey(),header.getValue());
                }
                connection.setRequestProperty("Host",rewrite_host);

                //连接
                connection.connect();
                //拿到response 直接转发 不需要进行处理
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder bs = new StringBuilder();
                String l;
                while ((l = bufferedReader.readLine()) != null) {
                    bs.append(l).append("\n");
                    System.out.println(l);
                }
                log.info(bs.toString());
                //不知道能不能直接转发response
                FullHttpResponse response = new DefaultFullHttpResponse(
                        HTTP_1_1,HttpResponseStatus.valueOf(connection.getResponseCode()), Unpooled.wrappedBuffer(bs != null ? bs.toString()
                        .getBytes() : new byte[0]));

                Map<String, List<String>> headerFields = connection.getHeaderFields();
                Set<String> keys = headerFields.keySet();
                for (String key : keys) {
                    if (key!=null){
                        String headerField = connection.getHeaderField(key);
                        response.headers().set(key,headerField);
                    }

                }
                ctx.write(response);
                ctx.flush();


            } catch (Exception e) {
                log.error("", e);
                return;
            }


        } else if (msg instanceof HttpResponse){


        }

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        ctx.close();
    }
}
