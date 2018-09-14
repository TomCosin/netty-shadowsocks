package com.shadow.socks;

import com.shadow.cipher.AesCrypt;
import com.shadow.cipher.ICrypt;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socks.SocksAddressType;
import io.netty.handler.codec.socks.SocksCmdResponse;
import io.netty.handler.codec.socks.SocksCmdStatus;
import io.netty.handler.codec.socks.SocksRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * 握手成功后处理请求并返回的handler
 * 代理操作
 * 将进入channel的数据转发给远程代理服务器
 * 1.新建bootStrap，连接到远程代理服务器
 * 2.使用连接后的channel向远程服务写出数据，并添加一个handler向远程服务写出数据
 * 3.接受远程代理服务器传入数据，在bootStrap的组中添加handler向本地写回数据
 */
public class SocksServerHandler extends SimpleChannelInboundHandler<SocksRequest> {

    private static final Logger logger = Logger.getAnonymousLogger();

    public ICrypt iCrypt;

    public SocksServerHandler() {
        //加密方式，及密码，需要填入
        this.iCrypt = new AesCrypt("aes-256-cfb", "password");
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, SocksRequest socksRequest) throws Exception {
        final Channel inboundChannel = ctx.channel();
        Bootstrap b = new Bootstrap();
        //设置启动参数
        b.group(inboundChannel.eventLoop()).channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000).option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        //绑定接收s端数据处理器
                        ch.pipeline().addLast(new AcceptHandler(ctx.channel(), iCrypt));
                    }
                });
        //连接服务器发送信息
        //服务器地址及端口号，需要填入
        b.connect("服务器地址", 443).addListener(new ChannelFutureListener() {
            //连接成功后向server发送信息
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    //原连接向本机输出成功
                    ctx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4));
                    //s端连接发送信息
                    future.channel().writeAndFlush(getBytes(socksRequest));
                    //此处理器连接成功后可删除
                    ctx.pipeline().remove(SocksServerHandler.this);
                    //添加向s端输出处理器
                    ctx.pipeline().addLast(new SendHandler(future.channel(), iCrypt));
                } else {
                    logger.info("connect_fail");
                }
            }
        });
    }

    private ByteBuf getBytes(SocksRequest socksRequest) throws IOException {
        ByteBuf buff = Unpooled.buffer();
        socksRequest.encodeAsByteBuf(buff);
        byte[] arr = new byte[buff.readableBytes() - 3];
        buff.getBytes(3, arr);
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        iCrypt.encrypt(arr, arr.length, bao);
        return Unpooled.wrappedBuffer(bao.toByteArray());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

}
