/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.client;

import io.netty.handler.ssl.SslContext;
import io.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import java.time.Duration;
import java.util.Properties;

public interface ZeebeClientBuilder {

  /**
   * Sets all the properties from a {@link Properties} object. Can be used to configure the client
   * from a properties file.
   *
   * <p>See {@link ClientProperties} for valid property names.
   */
  ZeebeClientBuilder withProperties(Properties properties);

  /**
   * @param contactPoint the IP socket address of a broker that the client can initially connect to.
   *     Must be in format <code>host:port</code>. The default value is <code>127.0.0.1:51015</code>
   *     .
   */
  ZeebeClientBuilder brokerContactPoint(String contactPoint);

  /**
   * @param maxJobsActive Default value for {@link JobWorkerBuilderStep3#maxJobsActive(int)}.
   *     Default value is 32.
   */
  ZeebeClientBuilder defaultJobWorkerMaxJobsActive(int maxJobsActive);

  /**
   * @param numThreads The number of threads for invocation of job workers. Setting this value to 0
   *     effectively disables subscriptions and workers. Default value is 1.
   */
  ZeebeClientBuilder numJobWorkerExecutionThreads(int numThreads);

  /**
   * The name of the worker which is used when none is set for a job worker. Default is 'default'.
   */
  ZeebeClientBuilder defaultJobWorkerName(String workerName);

  /** The timeout which is used when none is provided for a job worker. Default is 5 minutes. */
  ZeebeClientBuilder defaultJobTimeout(Duration timeout);

  /**
   * The interval which a job worker is periodically polling for new jobs. Default is 100
   * milliseconds.
   */
  ZeebeClientBuilder defaultJobPollInterval(Duration pollInterval);

  /** The time-to-live which is used when none is provided for a message. Default is 1 hour. */
  ZeebeClientBuilder defaultMessageTimeToLive(Duration timeToLive);

  /** The request timeout used if not overridden by the command. Default is 20 seconds. */
  ZeebeClientBuilder defaultRequestTimeout(Duration requestTimeout);

  /** Use a secure connection between the client and the gateway. */
  ZeebeClientBuilder useSecureConnection();

  /**
   * SSL/TLS context provided by {@link * GrpcSslContexts} to be used instead of the system default.
   */
  ZeebeClientBuilder sslContext(SslContext sslContext);

  /** @return a new {@link ZeebeClient} with the provided configuration options. */
  ZeebeClient build();
}
