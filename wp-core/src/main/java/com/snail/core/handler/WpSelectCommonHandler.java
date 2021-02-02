package com.snail.core.handler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.server.accept
 * @Description:
 * @date: 2021/02/02
 */
@Slf4j
public class WpSelectCommonHandler {

    private Selector[] selectorArr;

    private ExecutorService executorService;

    private SelectionKeyConsumer selectionKeyConsumer;

    //    待register的ChannelQueue
    private Queue<RegisterChannelInfo>[] registerChannelQueueArr;

    private ExecutorService handlerSelectExecutorService;

    private AtomicInteger indexGen = new AtomicInteger();

    private final int corePoolSize;

    public WpSelectCommonHandler(SelectionKeyConsumer selectionKeyConsumer) throws IOException {
        this(1, selectionKeyConsumer);
    }

    public WpSelectCommonHandler(int corePoolSize, SelectionKeyConsumer selectionKeyConsumer) throws IOException {
        this.corePoolSize = corePoolSize;

        this.selectionKeyConsumer = selectionKeyConsumer;
        handlerSelectExecutorService = new ThreadPoolExecutor(
            corePoolSize, corePoolSize * 2,
            30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(corePoolSize * 10),
            r -> new Thread(r, "WpSelectCommonHandler"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executorService = new ThreadPoolExecutor(
            corePoolSize, corePoolSize,
            0, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> new Thread(r, "WpSelectCommonSelector"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.registerChannelQueueArr = new Queue[corePoolSize];
        this.selectorArr = new Selector[corePoolSize];
        for (int i = 0; i < corePoolSize; i++) {
            registerChannelQueueArr[i] = new ConcurrentLinkedDeque<>();
            selectorArr[i] = Selector.open();
            int index = i;
            executorService.submit(() -> doSelector(index));
        }
    }

    private void doSelector(int index) {
        Selector selector = selectorArr[index];
        Queue<RegisterChannelInfo> registerChannelInfoQueue = this.registerChannelQueueArr[index];
        while (selector.isOpen()) {
            try {
                selector.select();
            } catch (IOException e) {
                log.error("select异常", e);
            }
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                try {
                    selectionKeyConsumer.accept(selectionKey);
                } catch (IOException e) {
                    log.error("消费selectionKey IO异常", e);
                    selectionKeyConsumer.afterIOEx(selectionKey);
                    selectionKey.cancel();
                }
                iterator.remove();
            }
            //            处理注册事件
            if (!registerChannelInfoQueue.isEmpty()) {
                Iterator<RegisterChannelInfo> selectableChannelIterator = registerChannelInfoQueue.iterator();
                while (selectableChannelIterator.hasNext()) {
                    RegisterChannelInfo registerChannelInfo = selectableChannelIterator.next();
                    SelectableChannel selectableChannel = registerChannelInfo.getSelectableChannel();
                    try {
                        SelectionKey selectionKey = selectableChannel.register(
                            selector,
                            registerChannelInfo.getOps(),
                            registerChannelInfo.getAtt()
                        );
                        Consumer<SelectionKey> selectionKeyConsumer = registerChannelInfo.getSelectionKeyConsumer();
                        if (selectionKeyConsumer != null) {
                            handlerSelectExecutorService.submit(() -> selectionKeyConsumer.accept(selectionKey));
                        }
                    } catch (ClosedChannelException e) {
                        log.error("注册selectableChannel异常", e);
                    }
                    selectableChannelIterator.remove();
                }
            }
        }

    }

    /**
     * 注册SelectableChannel到selector
     *
     * @param selectableChannel    channel
     * @param ops                  监听的类型
     * @param att                  自定义属性
     * @param selectionKeyConsumer 接受selectionKey的回到
     */
    public void registerChannel(SelectableChannel selectableChannel, int ops, Object att, Consumer<SelectionKey> selectionKeyConsumer) throws IOException {
        selectableChannel.configureBlocking(false);
        int index;
        if (corePoolSize == 1) {
            index = 0;
        } else {
            index = indexGen.incrementAndGet() % corePoolSize;
        }
        registerChannelQueueArr[index].add(new RegisterChannelInfo(selectableChannel, ops, att, selectionKeyConsumer));
//        唤醒selector以便处理注册队列
        selectorArr[index].wakeup();
    }

    @Data
    @AllArgsConstructor
    private class RegisterChannelInfo {
        private SelectableChannel selectableChannel;
        private int ops;
        private Object att;
        private Consumer<SelectionKey> selectionKeyConsumer;
    }

    @FunctionalInterface
    public interface SelectionKeyConsumer {

        void accept(SelectionKey selectionKey) throws IOException;

        default void afterIOEx(SelectionKey selectionKey) {
        }

        static SelectionKeyConsumer build(SelectionKeyConsumer selectionKeyConsumer, Consumer<SelectionKey> afterIOExFun) {
            return new SelectionKeyConsumer() {
                @Override
                public void accept(SelectionKey selectionKey) throws IOException {
                    selectionKeyConsumer.accept(selectionKey);
                }

                @Override
                public void afterIOEx(SelectionKey selectionKey) {
                    afterIOExFun.accept(selectionKey);
                }
            };
        }
    }

}
