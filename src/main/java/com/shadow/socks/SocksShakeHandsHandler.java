package com.shadow.socks;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socks.SocksAuthResponse;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksAuthStatus;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdRequestDecoder;
import io.netty.handler.codec.socks.SocksCmdType;
import io.netty.handler.codec.socks.SocksInitResponse;
import io.netty.handler.codec.socks.SocksRequest;

public class SocksShakeHandsHandler extends SimpleChannelInboundHandler<SocksRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocksRequest socksRequest) throws Exception {
        switch (socksRequest.requestType()) {
            case INIT: {
                ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
                ctx.writeAndFlush(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
                break;
            }
            case AUTH:
                ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
                ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
                break;
            case CMD:
                //握手成功，开始处理代理
                SocksCmdRequest req = (SocksCmdRequest) socksRequest;
                if (req.cmdType() == SocksCmdType.CONNECT) {
                    ctx.pipeline().addLast(new SocksServerHandler());
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(socksRequest);
                } else {
                    ctx.close();
                }
                break;
            case UNKNOWN:
                ctx.close();
                break;
            default:
                break;
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
