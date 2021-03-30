package com.snail.client;

import com.jediterm.pty.PtyMain;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.UIUtil;
import com.pty4j.PtyProcess;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.client
 * @Description:
 * @date: 2021/03/28
 */
public class MyPtyMain extends PtyMain {

    @Override
    public TtyConnector createTtyConnector() {
        try {
            Map<String, String> envs = new HashMap<>(System.getenv());
            String[] command;
            if (UIUtil.isWindows) {
                command = new String[]{"cmd.exe"};
            } else {
                command = new String[]{"/bin/bash", "--login"};
                envs.put("TERM", "xterm");
            }
            PtyProcess process = PtyProcess.exec(command, envs, null);
            return new PtyMain.LoggingPtyProcessTtyConnector(process, Charset.forName("UTF-8"));
        } catch (Exception var4) {
            throw new IllegalStateException(var4);
        }
    }


}
