/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.linkedin.pinot.core.realtime.impl.kafka;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.protocol.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import javax.annotation.Nullable;
import kafka.api.FetchRequestBuilder;
import kafka.api.PartitionOffsetRequestInfo;
import kafka.cluster.BrokerEndPoint;
import kafka.common.TopicAndPartition;
import kafka.javaapi.FetchResponse;
import kafka.javaapi.OffsetRequest;
import kafka.javaapi.OffsetResponse;
import kafka.javaapi.PartitionMetadata;
import kafka.javaapi.TopicMetadata;
import kafka.javaapi.TopicMetadataRequest;
import kafka.javaapi.TopicMetadataResponse;
import kafka.javaapi.consumer.SimpleConsumer;
import kafka.javaapi.message.ByteBufferMessageSet;
import kafka.message.MessageAndOffset;


/**
 * Wrapper for Kafka's SimpleConsumer which ensures that we're connected to the appropriate broker for consumption.
 */
public class SimpleConsumerWrapper implements Closeable {
  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleConsumerWrapper.class);
  private static final int SOCKET_TIMEOUT_MILLIS = 10000;
  private static final int SOCKET_BUFFER_SIZE = 512000;

  private enum ConsumerState {
    CONNECTING_TO_BOOTSTRAP_NODE,
    CONNECTED_TO_BOOTSTRAP_NODE,
    FETCHING_LEADER_INFORMATION,
    CONNECTING_TO_PARTITION_LEADER,
    CONNECTED_TO_PARTITION_LEADER
  }

  private State _currentState;

  private final String _clientId;
  private final boolean _metadataOnlyConsumer;
  private final String _topic;
  private final int _partition;
  private final KafkaSimpleConsumerFactory _simpleConsumerFactory;
  private String[] _bootstrapHosts;
  private int[] _bootstrapPorts;
  private SimpleConsumer _simpleConsumer;
  private final Random _random = new Random();
  private BrokerEndPoint _leader;
  private String _currentHost;
  private int _currentPort;

  private SimpleConsumerWrapper(KafkaSimpleConsumerFactory simpleConsumerFactory, String bootstrapNodes,
      String clientId) {
    _simpleConsumerFactory = simpleConsumerFactory;
    _clientId = clientId;
    _metadataOnlyConsumer = true;
    _simpleConsumer = null;

    // Topic and partition are ignored for metadata-only consumers
    _topic = null;
    _partition = Integer.MIN_VALUE;

    initializeBootstrapNodeList(bootstrapNodes);
    setCurrentState(new ConnectingToBootstrapNode());
  }

  private SimpleConsumerWrapper(KafkaSimpleConsumerFactory simpleConsumerFactory, String bootstrapNodes,
      String clientId, String topic, int partition) {
    _simpleConsumerFactory = simpleConsumerFactory;
    _clientId = clientId;
    _topic = topic;
    _partition = partition;
    _metadataOnlyConsumer = false;
    _simpleConsumer = null;

    initializeBootstrapNodeList(bootstrapNodes);
    setCurrentState(new ConnectingToBootstrapNode());
  }

  private void initializeBootstrapNodeList(String bootstrapNodes) {
    ArrayList<String> hostsAndPorts =
        Lists.newArrayList(Splitter.on(',').trimResults().omitEmptyStrings().split(bootstrapNodes));

    final int bootstrapHostCount = hostsAndPorts.size();

    if (bootstrapHostCount < 1) {
      throw new IllegalArgumentException("Need at least one bootstrap host");
    }

    _bootstrapHosts = new String[bootstrapHostCount];
    _bootstrapPorts = new int[bootstrapHostCount];

    for (int i = 0; i < bootstrapHostCount; i++) {
      String hostAndPort = hostsAndPorts.get(i);
      String[] splittedHostAndPort = hostAndPort.split(":");

      if (splittedHostAndPort.length != 2) {
        throw new IllegalArgumentException("Unable to parse host:port combination for " + hostAndPort);
      }

      _bootstrapHosts[i] = splittedHostAndPort[0];

      try {
        _bootstrapPorts[i] = Integer.parseInt(splittedHostAndPort[1]);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Could not parse port number " + splittedHostAndPort[1] + " for host:port combination " +  hostAndPort);
      }
    }
  }

  private abstract class State {
    private ConsumerState stateValue;

    protected State(ConsumerState stateValue) {
      this.stateValue = stateValue;
    }

    abstract void process();

    abstract boolean isConnectedToKafkaBroker();

    void handleConsumerException(Exception e) {
      // By default, just log the exception and switch back to CONNECTING_TO_BOOTSTRAP_NODE (which will take care of
      // closing the connection if it exists)
      LOGGER.warn("Caught Kafka consumer exception while in state {}, disconnecting and trying again",
          _currentState.getStateValue(), e);

      setCurrentState(new ConnectingToBootstrapNode());
    }

    ConsumerState getStateValue() {
      return stateValue;
    }
  }

  private class ConnectingToBootstrapNode extends State {
    public ConnectingToBootstrapNode() {
      super(ConsumerState.CONNECTING_TO_BOOTSTRAP_NODE);
    }

    @Override
    public void process() {
      // Connect to a random bootstrap node
      if (_simpleConsumer != null) {
        try {
          _simpleConsumer.close();
        } catch (Exception e) {
          LOGGER.warn("Caught exception while closing consumer, ignoring", e);
        }
      }

      int randomHostIndex = _random.nextInt(_bootstrapHosts.length);
      _currentHost = _bootstrapHosts[randomHostIndex];
      _currentPort = _bootstrapPorts[randomHostIndex];

      try {
        _simpleConsumer = _simpleConsumerFactory.buildSimpleConsumer(_currentHost, _currentPort, SOCKET_TIMEOUT_MILLIS,
            SOCKET_BUFFER_SIZE, _clientId);
        setCurrentState(new ConnectedToBootstrapNode());
      } catch (Exception e) {
        handleConsumerException(e);
      }
    }

    @Override
    boolean isConnectedToKafkaBroker() {
      return false;
    }
  }

  private class ConnectedToBootstrapNode extends State {
    protected ConnectedToBootstrapNode() {
      super(ConsumerState.CONNECTED_TO_BOOTSTRAP_NODE);
    }

    @Override
    void process() {
      if (_metadataOnlyConsumer) {
        // Nothing to do
      } else {
        // If we're consuming from a partition, we need to find the leader so that we can consume from it. By design,
        // Kafka only allows consumption from the leader and not one of the in-sync replicas.
        setCurrentState(new FetchingLeaderInformation());
      }
    }

    @Override
    boolean isConnectedToKafkaBroker() {
      return true;
    }
  }

  private class FetchingLeaderInformation extends State {
    public FetchingLeaderInformation() {
      super(ConsumerState.FETCHING_LEADER_INFORMATION);
    }

    @Override
    void process() {
      // Fetch leader information
      try {
        TopicMetadataResponse response = _simpleConsumer.send(new TopicMetadataRequest(Collections.singletonList(_topic)));
        try {
          _leader = null;
          List<PartitionMetadata> pMetaList = response.topicsMetadata().get(0).partitionsMetadata();
          for (PartitionMetadata pMeta : pMetaList) {
            if (pMeta.partitionId() == _partition) {
              _leader = pMeta.leader();
              break;
            }
          }

          // If we've located a broker
          if (_leader != null) {
            LOGGER.info("Located leader broker {}, connecting to it.", _leader);
            setCurrentState(new ConnectingToPartitionLeader());
          } else {
            // Failed to get the leader broker. There could be a leader election at the moment, so retry after a little
            // bit.
            LOGGER.warn("Leader broker is null, retrying leader fetch in 100ms");
            Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
          }
        } catch (Exception e) {
          // Failed to get the leader broker. There could be a leader election at the moment, so retry after a little
          // bit.
          LOGGER.warn("Failed to get the leader broker due to exception, retrying in 100ms", e);
          Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        }
      } catch (Exception e) {
        handleConsumerException(e);
      }
    }

    @Override
    boolean isConnectedToKafkaBroker() {
      return true;
    }
  }

  private class ConnectingToPartitionLeader extends State {
    public ConnectingToPartitionLeader() {
      super(ConsumerState.CONNECTING_TO_PARTITION_LEADER);
    }

    @Override
    void process() {
      // If we're already connected to the leader broker, don't disconnect and reconnect
      if (_leader.host().equals(_currentHost) && _leader.port() == _currentPort) {
        setCurrentState(new ConnectedToPartitionLeader());
        return;
      }

      // Disconnect from current broker
      if(_simpleConsumer != null) {
        try {
          _simpleConsumer.close();
          _simpleConsumer = null;
        } catch (Exception e) {
          handleConsumerException(e);
          return;
        }
      }

      // Connect to the partition leader
      try {
        _simpleConsumer =
            new SimpleConsumer(_leader.host(), _leader.port(), SOCKET_TIMEOUT_MILLIS, SOCKET_BUFFER_SIZE, _clientId);

        setCurrentState(new ConnectedToPartitionLeader());
      } catch (Exception e) {
        handleConsumerException(e);
      }
    }

    @Override
    boolean isConnectedToKafkaBroker() {
      return false;
    }
  }

  private class ConnectedToPartitionLeader extends State {
    public ConnectedToPartitionLeader() {
      super(ConsumerState.CONNECTED_TO_PARTITION_LEADER);
    }

    @Override
    void process() {
      // Nothing to do
    }

    @Override
    boolean isConnectedToKafkaBroker() {
      return true;
    }
  }

  private void setCurrentState(State newState) {
    if (_currentState != null) {
      LOGGER.info("Switching from state {} to state {}", _currentState.getStateValue(), newState.getStateValue());
    }

    _currentState = newState;
  }

  public synchronized int getPartitionCount(String topic, long maxWaitTimeMs) {
    int unknownTopicReplyCount = 0;
    final int MAX_UNKNOWN_TOPIC_REPLY_COUNT = 10;
    int kafkaErrorCount = 0;
    final int MAX_KAFKA_ERROR_COUNT = 10;

    while(true) {
      // Try to get into a state where we're connected to Kafka
      // TODO This needs a time limit, and perhaps a back-off before we query kafka again.
      while (!_currentState.isConnectedToKafkaBroker()) {
        _currentState.process();
      }

      // Send the metadata request to Kafka

      TopicMetadataResponse topicMetadataResponse = null;
      try {
        topicMetadataResponse = _simpleConsumer.send(new TopicMetadataRequest(Collections.singletonList(topic)));
      } catch (Exception e) {
        _currentState.handleConsumerException(e);
        continue;
      }

      final TopicMetadata topicMetadata = topicMetadataResponse.topicsMetadata().get(0);
      final short errorCode = topicMetadata.errorCode();

      if (errorCode == Errors.NONE.code()) {
        return topicMetadata.partitionsMetadata().size();
      } else if (errorCode == Errors.LEADER_NOT_AVAILABLE.code()) {
        // If there is no leader, it'll take some time for a new leader to be elected, wait 100 ms before retrying
        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
      } else if (errorCode == Errors.INVALID_TOPIC_EXCEPTION.code()) {
        throw new RuntimeException("Invalid topic name " + topic);
      } else if (errorCode == Errors.UNKNOWN_TOPIC_OR_PARTITION.code()) {
        if (MAX_UNKNOWN_TOPIC_REPLY_COUNT < unknownTopicReplyCount) {
          throw new RuntimeException("Topic " + topic + " does not exist");
        } else {
          // Kafka topic creation can sometimes take some time, so we'll retry after a little bit
          unknownTopicReplyCount++;
          Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        }
      } else {
        // Retry after a short delay
        kafkaErrorCount++;

        if (MAX_KAFKA_ERROR_COUNT < kafkaErrorCount) {
          throw Errors.forCode(errorCode).exception();
        }

        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
      }
    }
  }

  public synchronized Iterable<MessageAndOffset> fetchMessages(long startOffset, long endOffset, int timeoutMillis) {
    // Ensure that we're connected to the leader
    // TODO Add a timeout/error handling
    while(_currentState.getStateValue() != ConsumerState.CONNECTED_TO_PARTITION_LEADER) {
      _currentState.process();
    }

    FetchResponse fetchResponse = _simpleConsumer.fetch(new FetchRequestBuilder()
        .minBytes(100000)
        .maxWait(timeoutMillis)
        .addFetch(_topic, _partition, startOffset, 500000)
        .build());

    if (!fetchResponse.hasError()) {
      return buildOffsetFilteringIterable(fetchResponse.messageSet(_topic, _partition), startOffset, endOffset);
    } else {
      throw Errors.forCode(fetchResponse.errorCode(_topic, _partition)).exception();
    }
  }

  /**
   * Fetches the numeric Kafka offset for this partition for a symbolic name ("largest" or "smallest").
   *
   * @param requestedOffset Either "largest" or "smallest"
   * @param timeoutMillis Timeout in milliseconds
   * @return An offset
   */
  public synchronized long fetchPartitionOffset(String requestedOffset, int timeoutMillis) {
    Preconditions.checkNotNull(requestedOffset);

    final long offsetRequestTime;
    if (requestedOffset.equalsIgnoreCase("largest")) {
      offsetRequestTime = kafka.api.OffsetRequest.LatestTime();
    } else if (requestedOffset.equalsIgnoreCase("smallest")) {
      offsetRequestTime = kafka.api.OffsetRequest.EarliestTime();
    } else if (requestedOffset.equalsIgnoreCase("testDummy")) {
      return -1L;
    } else {
      throw new IllegalArgumentException("Unknown initial offset value " + requestedOffset);
    }

    int kafkaErrorCount = 0;
    final int MAX_KAFKA_ERROR_COUNT = 10;

    while(true) {
      // Try to get into a state where we're connected to Kafka
      // TODO This needs a time limit
      while (_currentState.getStateValue() != ConsumerState.CONNECTED_TO_PARTITION_LEADER) {
        _currentState.process();
      }

      // Send the offset request to Kafka
      OffsetRequest request = new OffsetRequest(Collections.singletonMap(new TopicAndPartition(_topic, _partition),
          new PartitionOffsetRequestInfo(offsetRequestTime, 1)), kafka.api.OffsetRequest.CurrentVersion(), _clientId);
      OffsetResponse offsetResponse;
      try {
        offsetResponse = _simpleConsumer.getOffsetsBefore(request);
      } catch (Exception e) {
        _currentState.handleConsumerException(e);
        continue;
      }

      final short errorCode = offsetResponse.errorCode(_topic, _partition);

      if (errorCode == Errors.NONE.code()) {
        long offset = offsetResponse.offsets(_topic, _partition)[0];
        if (offset == 0L) {
          LOGGER.warn("Fetched offset of 0 for topic {} and partition {}, is this a newly created topic?", _topic,
              _partition);
        }
        return offset;
      } else if (errorCode == Errors.LEADER_NOT_AVAILABLE.code()) {
        // If there is no leader, it'll take some time for a new leader to be elected, wait 100 ms before retrying
        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
      } else {
        // Retry after a short delay
        kafkaErrorCount++;

        if (MAX_KAFKA_ERROR_COUNT < kafkaErrorCount) {
          throw Errors.forCode(errorCode).exception();
        }

        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
      }
    }
  }

  private Iterable<MessageAndOffset> buildOffsetFilteringIterable(final ByteBufferMessageSet messageAndOffsets, final long startOffset, final long endOffset) {
    return Iterables.filter(messageAndOffsets, new Predicate<MessageAndOffset>() {
      @Override
      public boolean apply(@Nullable MessageAndOffset input) {
        // Filter messages that are either null or have an offset ∉ [startOffset; endOffset[
        if(input == null || input.offset() < startOffset || (endOffset <= input.offset() && endOffset != -1)) {
          return false;
        }

        // Check the message's checksum
        // TODO We might want to have better handling of this situation, maybe try to fetch the message again?
        if(!input.message().isValid()) {
          LOGGER.warn("Discarded message with invalid checksum in partition {} of topic {}", _partition, _topic);
          return false;
        }

        return true;
      }
    });
  }

  /**
   * Creates a simple consumer wrapper that connects to a random Kafka broker, which allows for fetching topic and
   * partition metadata. It does not allow to consume from a partition, since Kafka requires connecting to the
   * leader of that partition for consumption.
   *
   * @param simpleConsumerFactory The SimpleConsumer factory to use
   * @param bootstrapNodes A comma separated list of Kafka broker nodes
   * @param clientId The Kafka client identifier, to be used to uniquely identify the client when tracing calls
   * @return A consumer wrapper
   */
  public static SimpleConsumerWrapper forMetadataConsumption(KafkaSimpleConsumerFactory simpleConsumerFactory,
      String bootstrapNodes, String clientId) {
    return new SimpleConsumerWrapper(simpleConsumerFactory, bootstrapNodes, clientId);
  }

  /**
   * Creates a simple consumer wrapper that automatically connects to the leader broker for the given topic and
   * partition. This consumer wrapper can also fetch topic and partition metadata.
   *
   * @param simpleConsumerFactory The SimpleConsumer factory to use
   * @param bootstrapNodes A comma separated list of Kafka broker nodes
   * @param clientId The Kafka client identifier, to be used to uniquely identify the client when tracing calls
   * @param topic The Kafka topic to consume from
   * @param partition The partition id to consume from   @return A consumer wrapper
   */
  public static SimpleConsumerWrapper forPartitionConsumption(KafkaSimpleConsumerFactory simpleConsumerFactory,
      String bootstrapNodes, String clientId, String topic, int partition) {
    return new SimpleConsumerWrapper(simpleConsumerFactory, bootstrapNodes, clientId, topic, partition);
  }

  @Override
  /**
   * Closes this consumer.
   */
  public void close() throws IOException {
    boolean needToCloseConsumer = _currentState.isConnectedToKafkaBroker() && _simpleConsumer != null;

    // Reset the state machine
    setCurrentState(new ConnectingToBootstrapNode());

    // Close the consumer if needed
    if (needToCloseConsumer) {
      _simpleConsumer.close();
      _simpleConsumer = null;
    }
  }
}
