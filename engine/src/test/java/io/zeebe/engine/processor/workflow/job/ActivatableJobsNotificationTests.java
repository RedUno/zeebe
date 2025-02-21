/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.job;

import static io.zeebe.protocol.record.intent.JobIntent.TIMED_OUT;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.JobBatchRecordValue;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class ActivatableJobsNotificationTests {

  private static final String PROCESS_ID = "process";
  private static final Function<String, BpmnModelInstance> MODEL_SUPPLIER =
      (type) ->
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent("start")
              .serviceTask("task", b -> b.zeebeTaskType(type).done())
              .endEvent("end")
              .done();
  private static Consumer<String> jobAvailableCallback =
      (Consumer<String>) Mockito.spy(Consumer.class);

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobsAvailableCallback(jobAvailableCallback);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String taskType;

  @Before
  public void setup() {
    taskType = Strings.newRandomValidBpmnId();
    ENGINE.deployment().withXmlResource(PROCESS_ID, MODEL_SUPPLIER.apply(taskType)).deploy();
  }

  @Test
  public void shouldNotifyWhenFirstJobCreated() {
    // when
    createWorkflowInstanceAndJobs(1);

    // then
    Mockito.verify(jobAvailableCallback, times(1)).accept(taskType);
  }

  @Test
  public void shouldNotNotifyWhenSecondJobCreated() {
    // when
    createWorkflowInstanceAndJobs(2);

    // then
    Mockito.verify(jobAvailableCallback, times(1)).accept(taskType);
  }

  @Test
  public void shouldNotifyWhenJobsAvailableAgain() {
    // given
    createWorkflowInstanceAndJobs(2);
    final Record<JobBatchRecordValue> jobs = activateJobs(2);

    // when
    createWorkflowInstanceAndJobs(1);

    // then
    Mockito.verify(jobAvailableCallback, times(2)).accept(taskType);
  }

  @Test
  public void shouldNotifyWhenJobCanceled() {
    // given
    final List<Long> instanceKeys = createWorkflowInstanceAndJobs(1);
    ENGINE.workflowInstance().withInstanceKey(instanceKeys.get(0)).cancel();

    // when
    createWorkflowInstanceAndJobs(1);

    // then
    Mockito.verify(jobAvailableCallback, times(2)).accept(taskType);
  }

  @Test
  public void shouldNotNotifyWhenActivatedJobCanceled() {
    // given
    final List<Long> instanceKeys = createWorkflowInstanceAndJobs(2);
    activateJobs(1);
    ENGINE.workflowInstance().withInstanceKey(instanceKeys.get(0)).cancel();

    // when
    createWorkflowInstanceAndJobs(1);

    // then
    Mockito.verify(jobAvailableCallback, times(1)).accept(taskType);
  }

  @Test
  public void shouldNotifyWhenJobsAvailableAfterTimeOut() {
    // given
    createWorkflowInstanceAndJobs(1);
    activateJobs(1, Duration.ofMillis(10));

    // when
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    RecordingExporter.jobRecords(TIMED_OUT).withType(taskType).getFirst();

    // then
    Mockito.verify(jobAvailableCallback, times(2)).accept(taskType);
  }

  @Test
  public void shouldNotifyWhenJobAvailableAfterNotActivatedJobCompleted() {
    // given
    createWorkflowInstanceAndJobs(1);
    final long jobKey = activateJobs(1, Duration.ofMillis(10)).getValue().getJobKeys().get(0);
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    RecordingExporter.jobRecords(TIMED_OUT).withType(taskType).getFirst();

    // when
    ENGINE.job().withKey(jobKey).complete();
    createWorkflowInstanceAndJobs(1);

    // then
    Mockito.verify(jobAvailableCallback, times(3)).accept(taskType);
  }

  @Test
  public void shouldNotifyWhenJobsFailWithRetryAvailable() {
    // given
    createWorkflowInstanceAndJobs(1);
    final Record<JobBatchRecordValue> jobs = activateJobs(1);
    final long jobKey = jobs.getValue().getJobKeys().get(0);

    // when
    ENGINE.job().withKey(jobKey).withRetries(10).fail();

    // then
    Mockito.verify(jobAvailableCallback, times(2)).accept(taskType);
  }

  @Test
  public void shouldNotifyWhenFailedJobsResolved() {
    // given
    createWorkflowInstanceAndJobs(1);
    final Record<JobBatchRecordValue> jobs = activateJobs(1);
    final JobRecordValue job = jobs.getValue().getJobs().get(0);

    ENGINE.job().withType(taskType).ofInstance(job.getWorkflowInstanceKey()).fail();

    // when
    ENGINE
        .job()
        .ofInstance(job.getWorkflowInstanceKey())
        .withType(taskType)
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(job.getWorkflowInstanceKey()).resolve();

    // then
    Mockito.verify(jobAvailableCallback, times(2)).accept(taskType);
  }

  @Test
  public void shouldNotifyForMultipleJobTypes() {
    // given
    final String firstType = Strings.newRandomValidBpmnId();
    final String secondType = Strings.newRandomValidBpmnId();

    // when
    ENGINE.createJob(firstType, PROCESS_ID);
    ENGINE.createJob(secondType, PROCESS_ID);

    // then
    Mockito.verify(jobAvailableCallback, times(1)).accept(firstType);
    Mockito.verify(jobAvailableCallback, times(1)).accept(secondType);
  }

  private List<Long> createWorkflowInstanceAndJobs(int amount) {
    return IntStream.range(0, amount)
        .mapToObj(i -> ENGINE.createJob(taskType, PROCESS_ID))
        .map(r -> r.getValue().getWorkflowInstanceKey())
        .collect(Collectors.toList());
  }

  private Record<JobBatchRecordValue> activateJobs(int amount) {
    final Duration timeout = Duration.ofMinutes(12);
    return activateJobs(amount, timeout);
  }

  private Record<JobBatchRecordValue> activateJobs(int amount, Duration timeout) {
    final String worker = "myTestWorker";
    return ENGINE
        .jobs()
        .withType(taskType)
        .byWorker(worker)
        .withTimeout(timeout.toMillis())
        .withMaxJobsToActivate(amount)
        .activate();
  }
}
