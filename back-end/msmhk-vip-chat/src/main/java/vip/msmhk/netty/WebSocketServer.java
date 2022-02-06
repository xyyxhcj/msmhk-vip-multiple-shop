package vip.msmhk.netty;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.msmhk.common.constant.AppConsts;
import vip.msmhk.common.constant.ConfigConsts;
import vip.msmhk.common.entity.Msg;
import vip.msmhk.common.entity.MsgReply;
import vip.msmhk.common.entity.config.HkPage;
import vip.msmhk.common.entity.dto.MsgReplyDTO;
import vip.msmhk.common.entity.vo.MsgReplyVO;
import vip.msmhk.common.exception.ResultCode;
import vip.msmhk.common.exception.ServiceException;
import vip.msmhk.common.service.chat.MsgReplyService;
import vip.msmhk.common.service.chat.MsgService;
import vip.msmhk.common.util.FastUtils;
import vip.msmhk.common.util.JsonUtils;
import vip.msmhk.config.ChatConsts;
import vip.msmhk.config.ChatProperties;
import vip.msmhk.util.AesUtils;
import vip.msmhk.util.RSAUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.*;

/**
 * 使用主从线程组的nettyServer
 *
 * @author xyyxhcj@qq.com
 * @since 2021/06/25
 */
@Component
@Slf4j
public class WebSocketServer {
    /**
     * 定义主线程组，用于接受客户端连接
     */
    private final static EventLoopGroup MAIN_GROUP = new NioEventLoopGroup();
    /**
     * 定义从线程组，用于执行主线程组接受的任务
     */
    private final static EventLoopGroup SUB_GROUP = new NioEventLoopGroup();
    private static ChannelFuture CHANNEL_FUTURE;
    /**
     * 用于记录平台用户端的channel 小红点提醒，需要返回hasNewMsg及msgId
     **/
    private final static ChannelGroup PLATFORM_CLIENTS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final static Map<String, ChannelGroup> USER_CHANNELS_GROUP = new LinkedHashMap<>();
    /**
     * key:channelId,value[0]:userId,value[1]:aesKey
     */
    private final static Map<String, String[]> CHANNEL_DICT = new LinkedHashMap<>();
    @Resource
    private ChatProperties chatProperties;
    @Resource
    private MsgService msgService;
    @Resource
    private MsgReplyService msgReplyService;

    @PostConstruct
    public void init() {
        System.out.println(">>>>>>>>>>>>>>> chat-nettyServer init start <<<<<<<<<<<<<");
        // 使用启动类ServerBootstrap创建netty服务器
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(MAIN_GROUP, SUB_GROUP)
                .channel(NioServerSocketChannel.class)
                // 设置初始化器:channel注册后执行的初始化方法
                .childHandler(getChildHandler())
                //设置队列大小
                .option(ChannelOption.SO_BACKLOG, 1024)
                // 两小时内没有数据的通信时,TCP会自动发送一个活动探测数据报文
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        // 设置channel初始化器-每个channel由多个handler共同组成管道pipeline
        // 绑定监听端口
        CHANNEL_FUTURE = serverBootstrap.bind(chatProperties.getNettyPort());
        System.out.println(">>>>>>>>>>>>>>> chat-nettyServer init end <<<<<<<<<<<<<");

    }

    @PreDestroy
    public void destroy() {
        // 关闭channel
        CHANNEL_FUTURE.channel().closeFuture();
        // 关闭线程组
        MAIN_GROUP.shutdownGracefully();
        SUB_GROUP.shutdownGracefully();
    }

    private ChannelInitializer<SocketChannel> getChildHandler() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel channel){
                // 通过channel获取对应的管道,向管道pipeline中添加handler
                ChannelPipeline pipeline = channel.pipeline();
                // http编解码器
                pipeline.addLast(new HttpServerCodec());
                // 大数据流处理器
                pipeline.addLast(new ChunkedWriteHandler());
                // http聚合器,对httpMessage进行聚合
                pipeline.addLast(new HttpObjectAggregator(1024 * 64));
                // websocket处理器(用于处理握手,心跳,请求,响应,关闭,...)
                pipeline.addLast(new WebSocketServerProtocolHandler(chatProperties.getWebsocketPath()));
                // 自定义请求处理handler
                pipeline.addLast(getReqHandler());
            }
        };
    }

    /**
     * TextWebSocketFrame: netty中处理websocket文本的对象
     * Frame: 消息载体
     *
     * @return SimpleChannelInboundHandler<io.netty.handler.codec.http.websocketx.TextWebSocketFrame>
     * @author xyyxhcj@qq.com
     * @date 2020/4/18 21:56
     **/
    private SimpleChannelInboundHandler<TextWebSocketFrame> getReqHandler() {
        return new SimpleChannelInboundHandler<TextWebSocketFrame>() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                log.error("错误,{}", ctx.name(), cause);
                ctx.channel().closeFuture();
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
                log.info("start ------");
                Channel channel = ctx.channel();
                String channelId = channel.id().asLongText();
                // 客户端传输的消息 解密
                String reqText = msg.text();
                if (reqText.startsWith(StrUtil.DELIM_START)) {
                    // 消息未加密
                    MsgReplyDTO msgReplyDTO = JsonUtils.json2Pojo(reqText, MsgReplyDTO.class);
                    Byte wsType = msgReplyDTO.getWsType();
                    FastUtils.checkParams(wsType, msgReplyDTO.getCreateId(), msgReplyDTO.getKey());
                    if (!wsType.equals(ChatConsts.WsType.CONNECT)) {
                        return;
                    }
                    // 用户创建连接
                    addConnect(ctx, channel, channelId, msgReplyDTO);
                    return;
                }
                String aesKey = ArrayUtil.get(CHANNEL_DICT.get(channelId), 1);
                reqText = AesUtils.decrypt(reqText, aesKey);
                MsgReplyDTO msgReplyDTO = JsonUtils.json2Pojo(reqText, MsgReplyDTO.class);
                String userId = msgReplyDTO.getCreateId();
                Long msgId = msgReplyDTO.getMsgId();
                Byte replyType = msgReplyDTO.getType();
                FastUtils.checkParams(msgId, msgReplyDTO.getEnterpriseId(), msgReplyDTO.getWsType(), userId, replyType);
                synchronized (msgId.toString().intern()) {
                    switch (msgReplyDTO.getWsType()) {
                        case ChatConsts.WsType.DISCONNECT:
                            removeInvalidChannel(channel, userId);
                            break;
                        case ChatConsts.WsType.SEND_MSG:
                            dealSendMsg(ctx, aesKey, msgReplyDTO);
                            break;
                        case ChatConsts.WsType.GET_EARLIER_MSG:
                            HkPage<MsgReplyVO> page = msgReplyService.findPage(msgReplyDTO);
                            page.setWsType(ChatConsts.WsType.EARLIER_MSG);
                            ctx.writeAndFlush(getTextWebSocketFrame(page, aesKey));
                            break;
                        case ChatConsts.WsType.SET_PROCESSED:
                            dealSetProcessed(ctx, aesKey, msgReplyDTO);
                        default:
                    }
                }
                log.info("end ------");
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {
                Channel channel = ctx.channel();
                String channelId = channel.id().asLongText();
                // 刷新USER_CHANNELS_GROUP
                String[] removeChannel = CHANNEL_DICT.remove(channelId);
                if (removeChannel != null) {
                    String userId = removeChannel[0];
                    removeInvalidChannel(channel, userId);
                }
                // 当连接关闭时,CLIENTS会自动移除掉客户端的channel /CLIENTS
                log.info("客户端断开连接: {}, {}", Arrays.toString(removeChannel), channelId);
            }

            private void removeInvalidChannel(Channel channel, String userId) {
                ChannelGroup channels = USER_CHANNELS_GROUP.get(userId);
                if (channels != null) {
                    channels.remove(channel);
                    if (channels.isEmpty()) {
                        USER_CHANNELS_GROUP.remove(userId);
                    }
                }
            }
        };
    }

    private void dealSendMsg(ChannelHandlerContext ctx, String aesKey, MsgReplyDTO msgReplyDTO) throws Exception {
        FastUtils.checkParams(msgReplyDTO.getContent());
        msgReplyService.save(msgReplyDTO);
        msgReplyDTO.setWsType(ChatConsts.WsType.ADD_MSG_REPLY);
        // 触发标记‘发送成功’
        ctx.writeAndFlush(getTextWebSocketFrame(msgReplyDTO, aesKey));
        String otherUserId;
        Msg existMsg = msgService.getById(msgReplyDTO.getMsgId());
        FastUtils.checkNull(existMsg);
        boolean isUser = AppConsts.MsgReplyType.USER.equals(msgReplyDTO.getType());
        otherUserId = isUser ? existMsg.getHandlerId() : existMsg.getCreateId();
        ChannelGroup otherUserChannels = otherUserId == null ? PLATFORM_CLIENTS : USER_CHANNELS_GROUP.get(otherUserId);
        // 判断对方是否在线，是则向对方发送消息
        if (otherUserChannels != null) {
            msgReplyDTO.setHasNewMsg(true);
            for (Channel otherUserChannel : otherUserChannels) {
                String otherUserAesKey = ArrayUtil.get(CHANNEL_DICT.get(otherUserChannel.id().asLongText()), 1);
                if (otherUserAesKey == null) {
                    continue;
                }
                otherUserChannel.writeAndFlush(getTextWebSocketFrame(msgReplyDTO, otherUserAesKey));
            }
        }
    }

    /**
     * 通知系统，将channel与userId建立绑定
     **/
    private void addConnect(ChannelHandlerContext ctx, Channel channel, String channelId, MsgReplyDTO msgReplyDTO) throws Exception {
        String userId = msgReplyDTO.getCreateId();
        USER_CHANNELS_GROUP.computeIfAbsent(userId, k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)).add(channel);
        // 记录连接的userId,连接异常中断时及时刷新USER_CHANNELS_GROUP
        String aesKey = RSAUtils.decryptByPrivateKey(msgReplyDTO.getKey(), chatProperties.getRsaPrivateKey());
        if (!aesKey.startsWith(ChatConsts.Config.AES_KEY_PREFIX)) {
            log.error("aesKey非法: {}", aesKey);
            return;
        }
        String[] newChannel = {userId, aesKey};
        CHANNEL_DICT.put(channelId, newChannel);
        // 打开连接时,如果为平台用户，则放入Client中,当有新的用户反馈时通过其通知平台用户
        Byte replyType = msgReplyDTO.getType();
        if (AppConsts.MsgReplyType.PLATFORM.equals(replyType)) {
            PLATFORM_CLIENTS.add(ctx.channel());
            if (msgReplyDTO.getMsgId() != null) {
                // 如果有msgId参数，则返回历史消息
                HkPage<MsgReplyVO> page = msgReplyService.findPage(msgReplyDTO);
                page.setWsType(ChatConsts.WsType.HISTORY_MSG);
                ctx.writeAndFlush(getTextWebSocketFrame(page, aesKey));
            } else {
                // ‘存在无处理人的反馈’或‘处理人为当前用户，且消息未读’ -> 提示
                List<Msg> msgList = msgService.list(Wrappers.lambdaQuery(Msg.class)
                        .select(Msg::getMsgId, Msg::getHandlerId)
                        .eq(Msg::getStatus, AppConsts.MsgStatus.PROCESSING)
                                .and(orWrapper -> orWrapper.or(wrapper -> wrapper.isNull(Msg::getHandlerId))
                                        .or(wrapper -> wrapper.eq(Msg::getHandlerId, userId))));
                List<Long> acceptMsgIds = new LinkedList<>();
                Set<Object> hasNewMsgIds = new LinkedHashSet<>();
                boolean hasNewMsg = false;
                for (Msg msg : msgList) {
                    Long msgId = msg.getMsgId();
                    if (msg.getHandlerId() != null) {
                        // 已受理反馈
                        acceptMsgIds.add(msgId);
                    } else {
                        hasNewMsgIds.add(msgId);
                        hasNewMsg = true;
                    }
                }
                List<Object> notReadList = Collections.emptyList();
                if (!acceptMsgIds.isEmpty()) {
                    // 无未受理反馈
                    notReadList = msgReplyService.listObjs(Wrappers.lambdaQuery(MsgReply.class)
                            .select(MsgReply::getMsgId)
                            .in(MsgReply::getMsgId, acceptMsgIds)
                            .ne(MsgReply::getType, AppConsts.MsgReplyType.PLATFORM)
                            .eq(MsgReply::getIsRead, ConfigConsts.Is.NO));
                    if (!notReadList.isEmpty()) {
                        hasNewMsgIds.addAll(notReadList);
                    }
                }
                if (!hasNewMsg && notReadList.isEmpty()) {
                    // 无未受理反馈 且 处理中的反馈-无平台未读
                    return;
                }
                // 返回未读提醒
                msgReplyDTO.setHasNewMsg(true);
                msgReplyDTO.setMsgIds(hasNewMsgIds);
                msgReplyDTO.setWsType(ChatConsts.WsType.HAS_NEW_MSG);
                ctx.writeAndFlush(getTextWebSocketFrame(msgReplyDTO, aesKey));
            }

        } else {
            FastUtils.checkParams(msgReplyDTO.getMsgId());
            synchronized (msgReplyDTO.getMsgId().toString().intern()) {
                // 向 用户 返回历史消息
                HkPage<MsgReplyVO> page = msgReplyService.findPage(msgReplyDTO);
                page.setWsType(ChatConsts.WsType.HISTORY_MSG);
                ctx.writeAndFlush(getTextWebSocketFrame(page, aesKey));
            }
        }
        log.info("客户端连接: {}, {}", Arrays.toString(newChannel), channelId);

    }

    public void dealSetProcessed(ChannelHandlerContext ctx, String aesKey, MsgReplyDTO msgReplyDTO) throws Exception {
        String operatorId = msgReplyDTO.getCreateId();
        Msg existMsg = msgService.getById(msgReplyDTO.getMsgId());
        if (!existMsg.getStatus().equals(AppConsts.MsgStatus.PROCESSING)) {
            throw new ServiceException(ResultCode.FEEDBACK_STATUS_ERROR);
        }
        if (!operatorId.equals(existMsg.getHandlerId())) {
            throw new ServiceException(ResultCode.FEEDBACK_HANDLER_ERROR);
        }
        existMsg.setStatus(AppConsts.MsgStatus.PROCESSED);
        existMsg.setUpdateId(operatorId);
        msgService.updateById(existMsg);
        msgReplyDTO.setWsType(ChatConsts.WsType.MSG_PROCESSED);
        ctx.writeAndFlush(getTextWebSocketFrame(msgReplyDTO, aesKey));
        ChannelGroup otherUserChannels = USER_CHANNELS_GROUP.get(existMsg.getCreateId());
        if (otherUserChannels != null) {
            for (Channel otherUserChannel : otherUserChannels) {
                String otherUserAesKey = ArrayUtil.get(CHANNEL_DICT.get(otherUserChannel.id().asLongText()), 1);
                if (otherUserAesKey == null) {
                    continue;
                }
                otherUserChannel.writeAndFlush(getTextWebSocketFrame(msgReplyDTO, otherUserAesKey));
            }
        }
    }

    private TextWebSocketFrame getTextWebSocketFrame(Object obj, String aesKey) throws Exception {
        return new TextWebSocketFrame(AesUtils.encrypt(JsonUtils.object2Json(obj), aesKey));
    }
}
