/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.deployment.model.element.LoopCharacteristics;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.Arrays;
import java.util.Collections;

/**
 * Represents the "business logic" phase the element, so the base handler does nothing.
 *
 * @param <T>
 */
public class ElementActivatedHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  public ElementActivatedHandler() {
    this(WorkflowInstanceIntent.ELEMENT_COMPLETING);
  }

  public ElementActivatedHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    // TODO (saig0): clean up MI body - inner activity delegating code
    if (context.getElement() instanceof ExecutableActivity
        && ((ExecutableActivity) context.getElement()).hasLoopCharacteristics()) {

      if (context
          .getFlowScopeInstance()
          .getValue()
          .getElementIdBuffer()
          .equals(context.getElement().getId())) {
        // an inner instance
        return true;

      } else {
        // the multi instance body

        final VariablesState variablesState = context.getElementInstanceState().getVariablesState();
        final LoopCharacteristics loopCharacteristics =
            ((ExecutableActivity) context.getElement()).getLoopCharacteristics();
        final JsonPathQuery inputCollection = loopCharacteristics.getInputCollection();
        final DirectBuffer variableName = inputCollection.getVariableName();
        final DirectBuffer variablesAsDocument =
            variablesState.getVariablesAsDocument(
                context.getKey(), Collections.singleton(variableName));

        final MsgPackQueryProcessor queryProcessor = new MsgPackQueryProcessor();        ;

        final MsgPackQueryProcessor.QueryResult result =
            queryProcessor.process(inputCollection, variablesAsDocument).getSingleResult();

        final WorkflowInstanceRecord instanceRecord = context.getValue();

        // spawn instances
        final int size =
            result.readArray(
                item -> {
                  instanceRecord.setFlowScopeKey(context.getKey());
                  final long elementInstanceKey =
                      context
                          .getOutput()
                          .appendNewEvent(
                              WorkflowInstanceIntent.ELEMENT_ACTIVATING, instanceRecord);

                  // TODO (saig0): don't spawn token if children are created
                  context.getElementInstanceState().spawnToken(context.getKey());

                  loopCharacteristics
                      .getInputElement()
                      .ifPresent(
                          inputElement -> {
                            final MsgPackWriter msgPackWriter = new MsgPackWriter();
                            final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();
                            msgPackWriter.wrap(valueBuffer, 0);

                            Arrays.stream(inputElement.getPathElements())
                                .skip(1) // TODO (saig0): skip document root $
                                .forEach(
                                    path -> {
                                      msgPackWriter.writeMapHeader(1);
                                      msgPackWriter.writeString(BufferUtil.wrapString(path));
                                    });

                            msgPackWriter.writeRaw(item);

                            final DirectBuffer document =
                                new UnsafeBuffer(valueBuffer, 0, msgPackWriter.getOffset());
                            variablesState.setVariablesLocalFromDocument(
                                elementInstanceKey, instanceRecord.getWorkflowKey(), document);
                          });
                });

        if (size == 0) {
          // calling transition manually since next state is overwritten by super class
          transitionTo(context, WorkflowInstanceIntent.ELEMENT_COMPLETING);
        }

        // avoid to execute inner activity handler
        return false;
      }

    } else {
      // normal activity
      return true;
    }
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context)
        && isStateSameAsElementState(context)
        && (isRootScope(context) || isElementActive(context.getFlowScopeInstance()));
  }
}
