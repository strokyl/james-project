/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.modules.mailbox;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PerHostPercentileTracker;
import com.datastax.driver.core.QueryLogger;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.github.steveash.guavate.Guavate;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.init.ClusterBuilder;
import org.apache.james.backends.cassandra.init.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.backends.cassandra.init.QueryLoggerConfiguration;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.util.Host;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CassandraSessionModule extends AbstractModule {

    private static final int DEFAULT_CONNECTION_MAX_RETRIES = 10;
    private static final int DEFAULT_CONNECTION_MIN_DELAY = 5000;
    private static final long CASSANDRA_HIGHEST_TRACKABLE_LATENCY_MILLIS = TimeUnit.SECONDS.toMillis(10);

    @Override
    protected void configure() {
        bind(ScheduledExecutorService.class).toProvider(ScheduledExecutorServiceProvider.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().to(CassandraZonedDateTimeModule.class);
    }

    @Provides
    @Singleton
    CassandraModule composeDataDefinitions(Set<CassandraModule> modules) {
        return new CassandraModuleComposite(modules.toArray(new CassandraModule[0]));
    }

    @Provides
    @Singleton
    Session provideSession(CassandraSessionConfiguration configuration, Cluster cluster, CassandraModule cassandraModule)
            throws FileNotFoundException, ConfigurationException{
        String keyspace = configuration.getConfiguration().getString("cassandra.keyspace");
        return new SessionWithInitializedTablesFactory(cassandraModule).createSession(cluster, keyspace);
    }

    @Provides
    @Singleton
    CassandraSessionConfiguration getCassandraSessionConfiguration(FileSystem fileSystem) {
        return () -> getConfiguration(fileSystem);
    }

    @Provides
    @Singleton
    Cluster provideCluster(CassandraSessionConfiguration cassandraSessionConfiguration, AsyncRetryExecutor executor) throws FileNotFoundException, ConfigurationException, ExecutionException, InterruptedException {
        PropertiesConfiguration configuration = cassandraSessionConfiguration.getConfiguration();
        List<Host> servers = listCassandraServers(configuration);
        QueryLoggerConfiguration queryLoggerConfiguration = getCassandraQueryLoggerConf(configuration);

        return getRetryer(executor, configuration)
                .getWithRetry(ctx -> ClusterWithKeyspaceCreatedFactory
                        .config(
                            ClusterBuilder.builder()
                                .servers(servers)
                                .queryLoggerConfiguration(queryLoggerConfiguration)
                                .build(),
                            configuration.getString("cassandra.keyspace"))
                        .replicationFactor(configuration.getInt("cassandra.replication.factor"))
                        .clusterWithInitializedKeyspace())
                .get();
    }

    private List<Host> listCassandraServers(PropertiesConfiguration configuration) {
        String[] ipAndPorts = configuration.getStringArray("cassandra.nodes");

        return Arrays.stream(ipAndPorts)
                .map(string -> Host.parseConfString(string, ClusterBuilder.DEFAULT_CASSANDRA_PORT))
                .collect(Guavate.toImmutableList());
    }

    private Optional<Integer> getOptionalIntegerFromConf(PropertiesConfiguration configuration, String key) {
        return Optional.ofNullable(configuration.getInteger(key, null));
    }

    private Optional<Double> getOptionalDoubleFromConf(PropertiesConfiguration configuration, String key) {
        return Optional.ofNullable(configuration.getDouble(key, null));
    }

    private QueryLoggerConfiguration getCassandraQueryLoggerConf(PropertiesConfiguration configuration) {
        QueryLoggerConfiguration.Builder builder = QueryLoggerConfiguration.builder();

        Optional<Long> constantThreshold = getOptionalIntegerFromConf(configuration, "cassandra.query.logger.constant.threshold")
            .map(Long::valueOf);

        constantThreshold.ifPresent(builder::withConstantThreshold);

        getOptionalIntegerFromConf(configuration, "cassandra.query.logger.max.logged.parameters")
            .ifPresent(builder::withMaxLoggedParameters);

        getOptionalIntegerFromConf(configuration, "cassandra.query.logger.max.query.string.length")
            .ifPresent(builder::withMaxQueryStringLength);

        getOptionalIntegerFromConf(configuration, "cassandra.query.logger.max.parameter.value.length")
            .ifPresent(builder::withMaxParameterValueLength);

        Optional<Double> percentileLatencyConf = getOptionalDoubleFromConf(configuration, "cassandra.query.slow.query.latency.threshold.percentile");

        if (!percentileLatencyConf.isPresent() && !constantThreshold.isPresent()) {
            percentileLatencyConf = Optional.of(QueryLogger.DEFAULT_SLOW_QUERY_THRESHOLD_PERCENTILE);
        }

        percentileLatencyConf.ifPresent(slowQueryLatencyThresholdPercentile -> {
            PerHostPercentileTracker tracker = PerHostPercentileTracker
                .builderWithHighestTrackableLatencyMillis(CASSANDRA_HIGHEST_TRACKABLE_LATENCY_MILLIS)
                .build();

            builder.withDynamicThreshold(tracker, slowQueryLatencyThresholdPercentile);
        });

        return builder.build();
    }

    private static AsyncRetryExecutor getRetryer(AsyncRetryExecutor executor, PropertiesConfiguration configuration) {
        return executor.retryOn(NoHostAvailableException.class)
                .withProportionalJitter()
                .withMaxRetries(configuration.getInt("cassandra.retryConnection.maxRetries", DEFAULT_CONNECTION_MAX_RETRIES))
                .withMinDelay(configuration.getInt("cassandra.retryConnection.minDelay", DEFAULT_CONNECTION_MIN_DELAY));
    }

    @Provides
    private AsyncRetryExecutor provideAsyncRetryExecutor(ScheduledExecutorService scheduler) {
        return new AsyncRetryExecutor(scheduler);
    }

    private PropertiesConfiguration getConfiguration(FileSystem fileSystem) throws FileNotFoundException, ConfigurationException {
        return new PropertiesConfiguration(fileSystem.getFile(FileSystem.FILE_PROTOCOL_AND_CONF + "cassandra.properties"));
    }
}
