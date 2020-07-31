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

package com.wjybxx.fastjgame.concurrenttest;

import com.wjybxx.fastjgame.util.concurrent.DefaultThreadFactory;
import com.wjybxx.fastjgame.util.concurrent.EventLoop;
import com.wjybxx.fastjgame.util.concurrent.RejectedExecutionHandler;
import com.wjybxx.fastjgame.util.concurrent.unbounded.TemplateEventLoop;

/**
 * @author wjybxx
 * @version 1.0
 * date - 2020/6/2
 */
public class EventLoopSanityTestTemplate extends EventLoopSanityTest {

    @Override
    EventLoop newEventLoop(RejectedExecutionHandler rejectedExecutionHandler) {
        return new TemplateEventLoop(null, new DefaultThreadFactory("TEMPLATE_EVENT_LOOP"),
                rejectedExecutionHandler);
    }
}
