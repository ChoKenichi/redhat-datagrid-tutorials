package org.infinispan.tutorial.simple.distributed;

import java.util.UUID;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import org.infinispan.Cache;
import org.infinispan.CacheStream;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.context.Flag;
import org.infinispan.manager.ClusterExecutor;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.stream.CacheCollectors;

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
         // Store 1 to 1000
         System.out.println("Cache[" + cache.getName() + "] start.");
         IntStream.rangeClosed(1, 100).forEach(i -> cache.put("key" + i, "value" + i));


          try (CacheStream<Map.Entry<String, String>> stream = cache.entrySet().stream()) {
               System.out.println(stream.getClass());

               int result =
                       stream
                               //.parallel() //各ノードでさらに並列処理を行う場合
                               .map(e -> {   
                                  // クラスタ化されたCacheManagernでデータの分散処理を行う
                                  System.out.printf("value=%s\n", e.getValue());
                                  return Integer.parseInt(e.getValue().substring("value".length()));
                               })
                               // 並列処理した結果を集計する
                               .collect(CacheCollectors.serializableCollector(() -> Collectors.summingInt(v -> v)));


               System.out.println("Cache[" + cache.getName() + "] result = " + result);

	       int expected = 5050;
               if (result != expected) {
                   throw new IllegalStateException("result must be [" + expected + "]");
               }

          }
      }
      // Stop the cache manager and release all resources
      cacheManager.stop();
   }

}
