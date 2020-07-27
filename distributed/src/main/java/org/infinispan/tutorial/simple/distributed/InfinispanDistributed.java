package org.infinispan.tutorial.simple.distributed;

import java.util.UUID;
import java.util.Map;
import java.util.Comparator;

import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.context.Flag;
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
public class InfinispanDistributed {
   static final int MIN_SERVERS = 3;
   static final WaitObject obj = new WaitObject();

   public static void main(String[] args) throws Exception {
      // Setup up a clustered cache manager
      GlobalConfigurationBuilder global = GlobalConfigurationBuilder.defaultClusteredBuilder();
      // Initialize the cache manager
      DefaultCacheManager cacheManager = new DefaultCacheManager(global.build());
      //Create cache configuration
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.clustering().cacheMode(CacheMode.DIST_SYNC); //.hash().numOwners(2);
      // Obtain a cache
      Cache<String, String> cache = cacheManager.administration().withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
            .getOrCreateCache("cache", builder.build());
      if(cacheManager.getMembers().size() < MIN_SERVERS) {
         System.out.println("Waiting to start more cluster node");
         obj.suspend();
         
      }
      else {
         // Store the current node address in some random keys
         for (int i = 0; i < 10; i++) {
            cache.put(UUID.randomUUID().toString(), cacheManager.getNodeAddress());
         }
         // Display the current cache contents for the whole cluster
         System.out.println("===All cache Entries ===");
         cache.entrySet().forEach(entry -> System.out.printf("%s = %s\n", entry.getKey(), entry.getValue()));
         // 他のクラスタノードの処理を継続させる
         ClusterExecutor clusterExecutor = cacheManager.executor();
         clusterExecutor.submitConsumer(cm -> {
                obj.resume();
                return 0;
            }, (address, intValue, throwable) -> {
               // NOP
            });

      }
      // カレントノードのエントリをソートして出力する
      System.out.println("===This node's  cache Entries ===");
      cache.getAdvancedCache().withFlags(Flag.SKIP_REMOTE_LOOKUP).entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(entry -> System.out.printf("%s = %s\n", entry.getKey(), entry.getValue()));
      // Stop the cache manager and release all resources
      cacheManager.stop();
   }

}
