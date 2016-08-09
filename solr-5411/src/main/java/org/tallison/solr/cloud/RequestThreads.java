/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tallison.solr.cloud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/*
 * TODO: use ExecutorCompletionService instead if you can check hitmax and increment results
 */
public class RequestThreads<P> extends ArrayList<RequestWorker> implements ExecutorService {
  private static final long serialVersionUID = 7779485646159905867L;
  protected final ExecutorService exe;

  /**
   * This may not be the best place for a general-purpose configuration structure,
   * but this RequestStack will likely be passed around in different requesthandlers
   * with different types of parameters
   */
  protected P metadata;
  protected int currentIdx = 0;
  protected int i = 0;  // :D

  public RequestThreads(ExecutorService service) {
    this.exe = service;
  }
  public RequestThreads(ExecutorService service, P meta) {
    this.exe = service;
    this.metadata = meta;
  }

  public static <T> RequestThreads<T> newFixedThreadPool(int size) {
    ExecutorService exe = Executors.newFixedThreadPool(size);
    return new RequestThreads<T>(exe);
  }

  public P getMetadata() {
    return metadata;
  }

  public RequestThreads<P> setMetadata(P value) {
    metadata = value;
    return this;
  }

  @Override
  public void execute(Runnable command) {
    exe.execute(command);
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return exe.awaitTermination(timeout, unit);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return exe.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
    return exe.invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return exe.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return exe.invokeAny(tasks, timeout, unit);
  }

  @Override
  public boolean isShutdown() {
    return exe.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return exe.isTerminated();
  }

  @Override
  public void shutdown() {
    exe.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return exe.shutdownNow();
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return exe.submit(task);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return exe.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return exe.submit(task, result);
  }

  public void seal() {
    exe.shutdown();
  }


  public void addExecute(RequestWorker req) {
    super.add(req);
    exe.execute(req);
  }

  public boolean empty() {
    return this.size() == 0;
  }

  public RequestWorker next() {
    RequestWorker worker = null;
    if (size() > 0) {
      currentIdx = (++i % (this.size()));
      worker = this.get(currentIdx);
    }

    return worker;
  }

  public void removeLast() {
    if (size() > currentIdx)
      this.remove(currentIdx);
  }
}
