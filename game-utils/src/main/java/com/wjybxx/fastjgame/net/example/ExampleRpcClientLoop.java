/*
 * Copyright 2019 wjybxx
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wjybxx.fastjgame.net.example;

import com.wjybxx.fastjgame.net.local.LocalPort;
import com.wjybxx.fastjgame.net.local.LocalSessionConfig;
import com.wjybxx.fastjgame.net.misc.HostAndPort;
import com.wjybxx.fastjgame.net.misc.NetContext;
import com.wjybxx.fastjgame.net.rpc.DefaultRpcProcessor;
import com.wjybxx.fastjgame.net.session.Session;
import com.wjybxx.fastjgame.net.session.SessionDisconnectAware;
import com.wjybxx.fastjgame.net.socket.SocketSessionConfig;
import com.wjybxx.fastjgame.net.utils.NetUtils;
import com.wjybxx.fastjgame.util.concurrent.DefaultThreadFactory;
import com.wjybxx.fastjgame.util.concurrent.RejectedExecutionHandler;
import com.wjybxx.fastjgame.util.concurrent.RejectedExecutionHandlers;
import com.wjybxx.fastjgame.util.concurrent.disruptor.DisruptorEventLoop;
import com.wjybxx.fastjgame.util.concurrent.disruptor.YieldWaitStrategyFactory;
import com.wjybxx.fastjgame.util.time.TimeUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ThreadFactory;

/**
 * rpc请求客户端示例
 *
 * @version 1.2
 * date - 2019/8/26
 */
class ExampleRpcClientLoop extends DisruptorEventLoop {

    private final LocalPort localPort;
    private final ExampleRpcClientMgr rpcClientMgr = new ExampleRpcClientMgr();

    private Session session;
    private long startTime;
    private int index;

    public ExampleRpcClientLoop(@Nonnull ThreadFactory threadFactory,
                                @Nonnull RejectedExecutionHandler rejectedExecutionHandler,
                                @Nullable LocalPort localPort) {
        super(null, threadFactory, rejectedExecutionHandler, new YieldWaitStrategyFactory(), 64 * 1024, 64);
        this.localPort = localPort;
    }

    @Override
    protected void init() throws Exception {
        super.init();

        final String sessionId = "Session - " + System.currentTimeMillis();

        NetContext netContext = ExampleConstants.netEventLoop.createContext(this);

        if (localPort != null) {
            LocalSessionConfig config = LocalSessionConfig.newBuilder()
                    .setSerializer(ExampleConstants.BINARY_SERIALIZER)
                    .setLifecycleAware(new ServerDisconnectAward())
                    .setRpcProcessor(new DefaultRpcProcessor())
                    .build();

            session = netContext.connectLocal(sessionId, localPort, config).get();
        } else {
            // 必须先启动服务器
            SocketSessionConfig config = SocketSessionConfig.newBuilder()
                    .setSerializer(ExampleConstants.BINARY_SERIALIZER)
                    .setLifecycleAware(new ServerDisconnectAward())
                    .setRpcProcessor(new DefaultRpcProcessor())
//                    .setAutoReconnect(true)
                    .setAsyncRpcTimeoutMs((int) (15 * TimeUtils.SEC))
                    .setMaxPendingMessages(100)
                    .setMaxCacheMessages(20000)
                    .build();

            final HostAndPort address = new HostAndPort(NetUtils.getLocalIp(), ExampleConstants.tcpPort);
            session = netContext.connectTcp(sessionId, address, config)
                    .get();
        }
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void loopOnce() {
        if (session == null || System.currentTimeMillis() - startTime > TimeUtils.MIN) {
            shutdown();
            return;
        }
        sendRequest(index++);
    }

    private void sendRequest(final int index) {
        final long start = System.nanoTime();
        final String callResult = rpcClientMgr.syncCall(session, ExampleRpcServiceRpcProxy.combine("wjybxx", String.valueOf(index)));
        final long costTimeMs = System.nanoTime() - start;
        System.out.println("SyncCall - " + index + " - " + callResult + " , cost time nano " + costTimeMs);

        // 方法无返回值，也可以监听，只要调用的是call, sync, syncCall都可以获知调用结果，就像future
        rpcClientMgr.call(session, ExampleRpcServiceRpcProxy.hello("wjybxx- " + index))
                .addListener(future -> System.out.println("hello - " + index + " - " + future.getNow()));

        rpcClientMgr.call(session, ExampleRpcServiceRpcProxy.queryId("wjybxx-" + index))
                .addListener(future -> System.out.println("queryId - " + index + " - " + future.getNow()));

        rpcClientMgr.call(session, ExampleRpcServiceRpcProxy.inc(index))
                .addListener(future -> System.out.println("inc - " + index + " - " + future.getNow()));

        rpcClientMgr.call(session, ExampleRpcServiceRpcProxy.incWithContext(index))
                .addListener(future -> System.out.println("incWithSession - " + index + " - " + future.getNow()));

        rpcClientMgr.call(session, ExampleRpcServiceRpcProxy.incAsync(index))
                .addListener(future -> System.out.println("incAsync - " + index + " - " + future.getNow()));

        rpcClientMgr.call(session, ExampleRpcServiceRpcProxy.incWithContextAsync(index))
                .addListener(future -> System.out.println("incWithSessionAndPromise - " + index + " - " + future.getNow()));

        // 模拟场景服务器通过网关发送给玩家 - 注意：序列化方式必须一致。
        rpcClientMgr.call(session, ExampleRpcServiceRpcProxy.sendToPlayer(12345, "这里后期替换为protoBuf消息"))
                .addListener(future -> System.out.println("sendToPlayer - " + index + " - invoke success"));

        rpcClientMgr.call(session, ExampleRpcServiceRpcProxy.join("hello", "world"))
                .addListener(future -> System.out.println("joinResult " + future.getNow()));

        // 模拟玩家通过网关发送给场景服务器 - 注意：序列化方式必须一致。
        try {
            rpcClientMgr.call(session, ExampleRpcServiceRpcProxy.sendToScene(13245, ExampleConstants.BINARY_SERIALIZER.toBytes("这里后期替换为protoBuf消息")))
                    .addListener(future -> System.out.println("sendToScene - " + index + " - invoke success"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 阻塞到前面的rpc都返回，使得每次combine调用不被其它rpc调用影响
        // 因为调用的是sync(Session),对方的网络底层一定会返回一个结果，如果方法本身为void，那么返回的就是null。
        rpcClientMgr.syncCall(session, ExampleRpcServiceRpcProxy.sync());
    }

    @Override
    protected void clean() throws Exception {
        super.clean();
        ExampleConstants.netEventLoop.shutdown();
    }

    private class ServerDisconnectAward implements SessionDisconnectAware {

        @Override
        public void onSessionDisconnected(Session session) {
            System.out.println(" =========== onSessionDisconnected ==============");
            ExampleRpcClientLoop.this.session = null;
            // 断开连接后关闭
            shutdown();
        }
    }

    public static void main(String[] args) {
        ExampleRpcClientLoop echoClientLoop = new ExampleRpcClientLoop(
                new DefaultThreadFactory("CLIENT"),
                RejectedExecutionHandlers.discard(),
                null);

        // 唤醒线程
        echoClientLoop.execute(() -> {
        });
    }
}
