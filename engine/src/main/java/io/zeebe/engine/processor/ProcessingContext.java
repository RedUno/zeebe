/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor;

import io.zeebe.db.DbContext;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.util.sched.ActorControl;
import java.util.function.BooleanSupplier;

public class ProcessingContext implements ReadonlyProcessingContext {

  private ActorControl actor;
  private EventFilter eventFilter;
  private LogStream logStream;
  private LogStreamReader logStreamReader;
  private TypedStreamWriter logStreamWriter;
  private CommandResponseWriter commandResponseWriter;

  private RecordValueCache recordValueCache;
  private RecordProcessorMap recordProcessorMap;
  private ZeebeState zeebeState;
  private DbContext dbContext;

  private BooleanSupplier abortCondition;

  public ProcessingContext actor(ActorControl actor) {
    this.actor = actor;
    return this;
  }

  public ProcessingContext eventFilter(EventFilter eventFilter) {
    this.eventFilter = eventFilter;
    return this;
  }

  public ProcessingContext logStream(LogStream logStream) {
    this.logStream = logStream;
    return this;
  }

  public ProcessingContext logStreamReader(LogStreamReader logStreamReader) {
    this.logStreamReader = logStreamReader;
    return this;
  }

  public ProcessingContext eventCache(RecordValueCache recordValueCache) {
    this.recordValueCache = recordValueCache;
    return this;
  }

  public ProcessingContext recordProcessorMap(RecordProcessorMap recordProcessorMap) {
    this.recordProcessorMap = recordProcessorMap;
    return this;
  }

  public ProcessingContext zeebeState(ZeebeState zeebeState) {
    this.zeebeState = zeebeState;
    return this;
  }

  public ProcessingContext dbContext(DbContext dbContext) {
    this.dbContext = dbContext;
    return this;
  }

  public ProcessingContext abortCondition(BooleanSupplier abortCondition) {
    this.abortCondition = abortCondition;
    return this;
  }

  public ProcessingContext logStreamWriter(TypedStreamWriter logStreamWriter) {
    this.logStreamWriter = logStreamWriter;
    return this;
  }

  public ProcessingContext commandResponseWriter(CommandResponseWriter commandResponseWriter) {
    this.commandResponseWriter = commandResponseWriter;
    return this;
  }

  public ActorControl getActor() {
    return actor;
  }

  public EventFilter getEventFilter() {
    return eventFilter;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  public TypedStreamWriter getLogStreamWriter() {
    return logStreamWriter;
  }

  public RecordValueCache getRecordValueCache() {
    return recordValueCache;
  }

  public RecordProcessorMap getRecordProcessorMap() {
    return recordProcessorMap;
  }

  public ZeebeState getZeebeState() {
    return zeebeState;
  }

  public DbContext getDbContext() {
    return dbContext;
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return commandResponseWriter;
  }

  public BooleanSupplier getAbortCondition() {
    return abortCondition;
  }
}
