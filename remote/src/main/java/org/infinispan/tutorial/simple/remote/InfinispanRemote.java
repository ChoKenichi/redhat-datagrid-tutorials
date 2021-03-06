package org.infinispan.tutorial.simple.remote;

import org.infinispan.client.hotrod.DefaultTemplate;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.infinispan.commons.api.CacheContainerAdmin;

/**
 *
 * Infinispan Server includes a default property realm that requires
 * authentication. Create some credentials before you run this tutorial.
 *
 */
public class InfinispanRemote {

   public static void main(String[] args) {
      // Create a configuration for a locally-running server
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.addServer()
               //.host("127.0.0.1")
               //.port(ConfigurationProperties.DEFAULT_HOTROD_PORT)
               .host("datagrid-8-rhel8-rhdg.apps.ca-central-1.starter.openshift-online.com")
               .port(80)
             .security().authentication()
               //Add user credentials.
               .username("admin")
               .password("admin")
               .realm("default")
               .saslMechanism("DIGEST-MD5");
      // Connect to the server
      RemoteCacheManager cacheManager = new RemoteCacheManager(builder.build());
      // Create test cache, if such does not exist
      cacheManager.administration().withFlags(CacheContainerAdmin.AdminFlag.VOLATILE).getOrCreateCache("test", DefaultTemplate.DIST_SYNC);
      // Obtain the remote cache
      RemoteCache<String, String> cache = cacheManager.getCache("test");
      /// Store a value
      cache.put("key", "value");
      // Retrieve the value and print it out
      System.out.printf("key = %s\n", cache.get("key"));
      // Stop the cache manager and release all resources
      cacheManager.stop();
   }

}
