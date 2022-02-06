package vip.msmhk.config;

/**
 * @author xyyxhcj@qq.com
 * @since 2021-03-27
 */

public interface ChatConsts {
    interface Config{
        String AES_KEY_PREFIX = "hk";
    }
    interface WsType {
        byte DISCONNECT = -1;
        byte CONNECT = 1;
        byte SEND_MSG = 2;
        // 获取更早的信息
        byte GET_EARLIER_MSG = 3;
        // 设为已处理
        byte SET_PROCESSED = 4;

        // 创建连接时，首次返回的历史reply记录
        byte HISTORY_MSG = 20;
        // 通知H5:收到一条聊天记录
        byte ADD_MSG_REPLY = 21;
        // 下拉时返回更早的消息
        byte EARLIER_MSG = 22;
        byte HAS_NEW_MSG = 23;
        // 反馈已处理完毕
        byte MSG_PROCESSED = 24;

    }
}
