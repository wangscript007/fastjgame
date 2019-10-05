/*
 *  Copyright 2019 wjybxx
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to iBn writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.wjybxx.fastjgame.net.session;

import com.wjybxx.fastjgame.concurrent.EventLoop;
import com.wjybxx.fastjgame.concurrent.Promise;
import com.wjybxx.fastjgame.eventloop.NetEventLoop;
import com.wjybxx.fastjgame.manager.NetManagerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 默认的{@link SessionPipeline}实现。
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/9/25
 * github - https://github.com/hl845740757
 */
class DefaultSessionPipeline implements SessionPipeline {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSessionPipeline.class);

    private final Session session;
    private final NetManagerWrapper netManagerWrapper;

    /**
     * 尾部处理器 - 出站第一个处理器，入站最后一个处理器
     */
    private final TailContext tail;
    /**
     * 头部处理器 - 入站事件的第一个处理器，出站事件的最后一个处理器
     */
    private final HeadContext head;

    DefaultSessionPipeline(Session session, NetManagerWrapper netManagerWrapper) {
        this.session = session;
        this.netManagerWrapper = netManagerWrapper;
        this.tail = new TailContext(this, netManagerWrapper);
        this.head = new HeadContext(this, netManagerWrapper);

        head.next = tail;
        tail.prev = head;
    }

    @Override
    public Session session() {
        return session;
    }

    @Override
    public NetEventLoop netEventLoop() {
        return session.netEventLoop();
    }

    @Override
    public EventLoop localEventLoop() {
        return session.localEventLoop();
    }

    @Override
    public SessionPipeline addLast(SessionHandler handler) {
        final AbstractSessionHandlerContext newCtx = new DefaultSessionHandlerContext(this, netManagerWrapper, handler);
        addLast0(newCtx);
        return this;
    }

    private void addLast0(AbstractSessionHandlerContext newCtx) {
        final AbstractSessionHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;
    }

    @Override
    public SessionPipeline addFirst(SessionHandler handler) {
        final AbstractSessionHandlerContext newCtx = new DefaultSessionHandlerContext(this, netManagerWrapper, handler);
        addFirst0(newCtx);
        return this;
    }

    private void addFirst0(AbstractSessionHandlerContext newCtx) {
        AbstractSessionHandlerContext nextCtx = head.next;
        newCtx.prev = head;
        newCtx.next = nextCtx;
        head.next = newCtx;
        nextCtx.prev = newCtx;
    }

    // ------------------------------------------------- inbound -----------------------------------------------------

    @Override
    public void fireSessionActive() {
        head.fireSessionActive();
    }

    @Override
    public void fireSessionInactive() {
        head.fireSessionInactive();
    }

    @Override
    public void fireRead(@Nullable Object msg) {
        head.fireRead(msg);
    }

    @Override
    public void fireExceptionCaught(Throwable cause) {
        head.fireExceptionCaught(cause);
    }

    @Override
    public SessionPipeline fireInit() {
        AbstractSessionHandlerContext context = head;
        do {
            context.init();
            context = context.next;
        } while (context != null);
        return this;
    }

    @Override
    public void tick() {
        AbstractSessionHandlerContext context = head;
        do {
            context.tick();
            context = context.next;
        } while (context != null);
    }

    // ------------------------------------------------- outbound -----------------------------------------------------

    @Override
    public void fireWrite(@Nonnull Object msg) {
        tail.fireWrite(msg);
    }

    @Override
    public void fireFlush() {
        tail.fireFlush();
    }

    @Override
    public void fireWriteAndFlush(@Nonnull Object msg) {
        tail.fireWriteAndFlush(msg);
    }

    @Override
    public void fireClose(Promise<?> promise) {
        tail.fireClose(promise);
        head.fireSessionInactive();
    }

    // ------------------------------------------------- inner -----------------------------------------------------

    /**
     * 头部处理器 - 头部处理器必须实现{@link SessionOutboundHandler}。
     * Q: 它为什么必须实现{@link SessionOutboundHandler} ？
     * A: 它是出站的最后的一个处理器，执行一些默认逻辑，以及避免向下传递事件时没有下一个handler。
     */
    private static class HeadContext extends AbstractSessionHandlerContext implements SessionOutboundHandler {

        HeadContext(DefaultSessionPipeline pipeline, NetManagerWrapper netManagerWrapper) {
            super(pipeline, netManagerWrapper);
        }

        @Override
        public SessionHandler handler() {
            return this;
        }

        @Override
        public void init(SessionHandlerContext ctx) throws Exception {
            // NO OP
        }

        @Override
        public void tick(SessionHandlerContext ctx) {
            // NO OP
        }

        @Override
        public void write(SessionHandlerContext ctx, Object msg) {
            logger.info("Unhandled outboundMessage " + msg.getClass().getName());
        }

        @Override
        public void flush(SessionHandlerContext ctx) throws Exception {
            logger.info("Unhandled flush request");
        }

        @Override
        public void close(SessionHandlerContext ctx, Promise<?> promise) throws Exception {
            logger.info("Unhandled closeEvent");
        }
    }

    /**
     * 尾部处理器 - 尾部处理器需要实现{@link SessionInboundHandler}。
     * Q: 为什么必须实现{@link SessionInboundHandler} ?
     * A: 它是入站的最后一个处理器，执行一些默认逻辑，以及避免入站事件向下传递时没有handler。
     */
    private static class TailContext extends AbstractSessionHandlerContext implements SessionInboundHandler {

        TailContext(DefaultSessionPipeline pipeline, NetManagerWrapper managerWrapper) {
            super(pipeline, managerWrapper);
        }

        @Override
        public SessionHandler handler() {
            return this;
        }

        @Override
        public void init(SessionHandlerContext ctx) throws Exception {
            // NO OP
        }

        @Override
        public void tick(SessionHandlerContext ctx) {
            // NO OP
        }

        @Override
        public void onSessionActive(SessionHandlerContext ctx) throws Exception {
            // NO OP
        }

        @Override
        public void onSessionInactive(SessionHandlerContext ctx) throws Exception {
            // NO OP
        }

        @Override
        public void read(SessionHandlerContext ctx, Object msg) {
            if (null != msg) {
                logger.info("Unhandled inboundMessage {}", msg.getClass().getName());
            }
        }

        @Override
        public void onExceptionCaught(SessionHandlerContext ctx, Throwable cause) throws Exception {
            logger.info("Unhandled exception", cause);
        }
    }
}