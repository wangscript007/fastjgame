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

package com.wjybxx.fastjgame.test;

import com.wjybxx.fastjgame.concurrent.DefaultThreadFactory;
import com.wjybxx.fastjgame.concurrent.RejectedExecutionHandlers;
import com.wjybxx.fastjgame.configwrapper.ArrayConfigWrapper;
import com.wjybxx.fastjgame.eventloop.NetEventLoopGroup;
import com.wjybxx.fastjgame.eventloop.NetEventLoopGroupImp;
import com.wjybxx.fastjgame.module.LoginModule;
import com.wjybxx.fastjgame.world.GameEventLoopGroup;
import com.wjybxx.fastjgame.world.GameEventLoopGroupImp;

import java.io.File;

/**
 * loginserver测试用例。
 *
 * 启动参数：
 * port=8989
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/5/18 15:46
 * github - https://github.com/hl845740757
 */
public class LoginWorldTest {

    public static void main(String[] args) throws Exception {
        String logDir=new File("").getAbsolutePath() + File.separator + "log";
        String logPath = logDir + File.separator + "login.log";
        System.setProperty("logPath", logPath);

        NetEventLoopGroup netEventLoopGroup = new NetEventLoopGroupImp(1, new DefaultThreadFactory("NET"), RejectedExecutionHandlers.reject());
        GameEventLoopGroup gameEventLoopGroup = new GameEventLoopGroupImp(1, new DefaultThreadFactory("LOGIC-WORLD"), RejectedExecutionHandlers.reject(), netEventLoopGroup);

        gameEventLoopGroup.registerWorld(new LoginModule(), new ArrayConfigWrapper(args), 5);
    }
}
