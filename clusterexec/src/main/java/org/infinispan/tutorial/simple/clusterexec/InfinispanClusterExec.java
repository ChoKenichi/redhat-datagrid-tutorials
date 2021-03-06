package org.infinispan.tutorial.simple.clusterexec;

import java.io.Serializable;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.ClusterExecutor;
import org.infinispan.manager.DefaultCacheManager;

class WaitObject {

    // このオブジェクトを使って待機状態に入る
    public synchronized void suspend() throws InterruptedException {
        this.wait();
    }
    
    // このオブジェクトを使って待機しているスレッドを全て起こす
    public synchronized void resume() {
        this.notifyAll();
    }
}

public class InfinispanClusterExec {

    static final int MIN_SERVERS = 3;
    static final WaitObject obj = new WaitObject();

    public static void main(String[] args) throws Exception {
        // Setup up a clustered cache manager
        GlobalConfigurationBuilder global = GlobalConfigurationBuilder.defaultClusteredBuilder();
        // Initialize the cache manager
        DefaultCacheManager cacheManager = new DefaultCacheManager(global.build());
        System.out.printf("\nI am [%s]\n\n", cacheManager.getAddress());

        // クラスタメンバが一定数以上の場合分散処理を実行する
        if (cacheManager.getMembers().size() >= MIN_SERVERS) {
            ClusterExecutor clusterExecutor = cacheManager.executor();
            CompletableFuture<Void> completableFuture = clusterExecutor.submitConsumer(cm -> {
                // 各Data Grid ノードで実行される処理（コーディング量を減らすためにラムダ式で記述）
                int i = new Random().nextInt(); // ランダム値を復帰値とする
                System.out.printf("Tread[%s] callable, value[%d]\n", Thread.currentThread(), i);
                //obj.resume();
                return i;
            }, (address, intValue, throwable) -> {
                // 各ノードの処理結果を受け取る処理(コーディングを簡単にするためラムダ式で記述)
                // Functionが実行されたAddress（Node）、Functionが返した値、例外が発生した場合はその原因
                if (throwable != null) {
                    System.out.println("Address: " + address + " encountered an error: " + throwable);
                    throwable.printStackTrace();
                }
                System.out.printf("Address[%s], value[%s]\n", address, intValue);
            });

            completableFuture.whenComplete((v, t) -> {
                // 全部のノードの処理が終了したら行う処理
                if (t != null) {
                    System.out.println("Exception encountered while waiting:" + t);
                    t.printStackTrace();
                }
                System.out.println("Distributed process compleated.");
            });
        } else {
            System.out.println("Waiting to start more cluster node");
            obj.suspend();
            Thread.yield();
        }
        // Shuts down the cache manager and all associated resources
        cacheManager.stop();
    }
}
