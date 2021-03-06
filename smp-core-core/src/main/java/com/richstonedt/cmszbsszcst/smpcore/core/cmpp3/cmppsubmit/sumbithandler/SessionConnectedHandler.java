

package com.richstonedt.cmszbsszcst.smpcore.core.cmpp3.cmppsubmit.sumbithandler;

import com.zx.sms.codec.cmpp.msg.*;
import com.zx.sms.handler.api.AbstractBusinessHandler;
import com.zx.sms.session.cmpp.SessionState;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

//import org.apache.commons.lang.StringUtils;
//import org.apache.commons.lang.math.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 此类为短信发送业务核心处理类，根据消息体的电话号码，短信内容发送。
 *
 * @author zengweijie
 * @since smp-core 1.0.0
 */
@ChannelHandler.Sharable
public class SessionConnectedHandler extends AbstractBusinessHandler implements Cloneable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionConnectedHandler.class);

    /**
     * 用作标志性的开关,0表示unLock ,1表示Lock
     */
    private volatile AtomicInteger flag;

    /**
     * 短信任务发送数量，使用原子操作，保证线程的可见性。
     */
    private volatile AtomicInteger totleCnt;

    /**
     * 短信发送号码队列
     */
    private BlockingQueue phone;


    /**
     * 待发送的短信内容
     */
    private String contentMessage;

    /**
     * LockSurport 用于执行unpark
     */
    private Thread thread;

    private static final String DELIVRD = "DELIVRD";

    /**
     * 处理多线程的电话号码获取的方法。保证队列的号码以队列的形式
     */
    public String get() throws InterruptedException {
        return (String) phone.poll(10, TimeUnit.MILLISECONDS);
    }

    public SessionConnectedHandler() {
    }

    public SessionConnectedHandler(int totleCnt, BlockingQueue<String> phone,
                                   Thread thread, String contentMessage, AtomicInteger flag) {
        this.totleCnt = new AtomicInteger(totleCnt);
        this.phone = phone;
        this.thread = thread;
        this.contentMessage = contentMessage;
        this.flag = flag;
    }

    /**
     * handler触发器
     */
    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == SessionState.Connect) {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof CmppConnectResponseMessage) {
            CmppConnectResponseMessage c = (CmppConnectResponseMessage) msg;
            LOGGER.debug("======响应连接start======");
            //连接响应消息，是否连接成功
            LOGGER.debug("响应状态：{}", String.valueOf(c.getStatus()));
            LOGGER.debug("服务支持版本：{}", String.valueOf(c.getVersion()));
            LOGGER.debug("======响应连接end======");

        } else if (msg instanceof CmppDeliverRequestMessage) {
            LOGGER.debug("接收回执消息,用于确认网关是否发送短信成功=====start=====");
            CmppDeliverRequestMessage e = (CmppDeliverRequestMessage) msg;
            CmppDeliverResponseMessage responseMessage = new CmppDeliverResponseMessage(e.getHeader().getSequenceId());
            //此处表示网关成功推送消息的回执
            if (DELIVRD.equals(e.getReportRequestMessage().getStat())) {
                LOGGER.debug("网关推送消息成功，成功消息为：{}", e.getReportRequestMessage().getMsgId());
            } else {
                LOGGER.debug("=============失败代号stat为=============" + e.getReportRequestMessage().getStat());
                LOGGER.debug("网关推送消息失败，失败消息为：{}", e.getReportRequestMessage().getMsgId());
                LOGGER.debug("=============推送失败，推送失败全家桶消息:***********{}", e.getReportRequestMessage());
            }
            LOGGER.debug("接收回执消息,用于确认网关是否发送短信成功=====end=====");
            //回复CMPPDeliver请求。
            responseMessage.setResult(0);
            responseMessage.setMsgId(e.getMsgId());
            ctx.channel().writeAndFlush(responseMessage);
        } else if (msg instanceof CmppSubmitResponseMessage) {
            CmppSubmitResponseMessage responseMessage = (CmppSubmitResponseMessage) msg;
            LOGGER.debug("======网关接收请求回执CmppSubmitResp start======");
            //连接响应消息，是否连接成功
            LOGGER.debug("CmppSubmitResp回执消息：id: {},result: {},seq: {}",
                    responseMessage.getMsgId(),
                    responseMessage.getResult(),
                    responseMessage.getSequenceNo());
            if (responseMessage.getResult() == 0L) {
                LOGGER.debug("======网关接收成功======");
            } else {
                LOGGER.debug("======网关接收失败======");
            }
            LOGGER.debug("======网关接收请求回执CmppSubmitResp end======");
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public String name() {
        return "SessionConnectedHandler-Gate";
    }

    @Override
    public SessionConnectedHandler clone() throws CloneNotSupportedException {
        SessionConnectedHandler ret = (SessionConnectedHandler) super.clone();
        return ret;
    }
}
