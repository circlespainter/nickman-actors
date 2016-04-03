/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.heliosapm.actors;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.MailboxConfig;
import co.paralleluniverse.actors.behaviors.BehaviorActor;
import co.paralleluniverse.actors.behaviors.ProxyServerActor;
import co.paralleluniverse.actors.behaviors.Supervisor;
import co.paralleluniverse.actors.behaviors.Supervisor.ChildMode;
import co.paralleluniverse.actors.behaviors.Supervisor.ChildSpec;
import co.paralleluniverse.actors.behaviors.SupervisorActor;
import co.paralleluniverse.actors.behaviors.SupervisorActor.RestartStrategy;
import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Strand;
import co.paralleluniverse.strands.channels.Channels.OverflowPolicy;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>Title: ActorManager</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.actors.ActorManager</code></p>
 */

public final class ActorManager {
    private static volatile AtomicReference<ActorManager> instance = new AtomicReference<>(null);

    private final NonBlockingHashMap<String, PosAcct> posAccts = new NonBlockingHashMap<>();
    private final Supervisor supervisor;

    private ChildMode mode = ChildMode.PERMANENT;
    private TimeUnit durationUnit = TimeUnit.SECONDS;

    private int registered = 0;

    private static ActorManager getInstance() throws InterruptedException, SuspendExecution {
        instance.compareAndSet(null, new ActorManager());
        return instance.get();
    }

    public static void main(String[] args) throws InterruptedException, SuspendExecution, ExecutionException {
        new Fiber(() -> {
            log("PosAcct Test");
            final ActorManager am = getInstance();

            log("Loading Actors...");
            final Iterator<PosAcct> posIter = PosAcctImpl.load();
            int loaded = 0;
            boolean idproxy = false;
            while (posIter.hasNext()) {
                if (idproxy)
                    am.registerIDProxy(posIter.next());
                else
                    am.registerProxy(posIter.next());
                idproxy = !idproxy;
                loaded++;
            }
            log("Loaded " + loaded + " Actors");

            for (int i = 0; i < 100; i++) {
                log("Total: " + depositAll());
            }

            Strand.sleep(10_000_000_000_000L);
        }).start().join();
    }

    private static void log(String s) {
        System.err.println(s);
    }

    private static int depositAll() throws SuspendExecution, InterruptedException {
        final ActorManager am = getInstance();
        int x = 0;
        for (final PosAcct pa : am.posAccts.values()) {
            pa.deposit(new BigDecimal(1));
            x++;
        }
        return x;
    }

    private PosAcct registerProxy(final PosAcct posAcct) throws SuspendExecution, InterruptedException {
        final ProxyServerActor proxy = new ProxyServerActor (
            false,
            posAcct,
            PosAcct.class
        ) {
            @Override
            public final String getName() {
                return posAcct.getName();
            }

            @Override
            protected final void handleMessage(Object m) throws InterruptedException, SuspendExecution {
                // TODO Override proxy behaviour if needed
                ActorManager.log("Overridden handleMessage");
                super.handleMessage(m);
            }
        };
        return spawn(posAcct, proxy);
    }

    private PosAcct registerIDProxy(final PosAcct posAcct) throws SuspendExecution, InterruptedException {
        final IDProxyServerActor proxy = new IDProxyServerActor (
            posAcct.getName(),
            new MailboxConfig(10, OverflowPolicy.BACKOFF),
            true,
            posAcct,
            PosAcct.class
        ) {
            @Override
            public final String getName() {
                return posAcct.getName();
            }
        };

        return spawn(posAcct, proxy);
    }

    private PosAcct spawn(PosAcct posAcct, BehaviorActor proxy) throws SuspendExecution, InterruptedException {
        final ActorRef<?> proxyRef = proxy.spawn();
        if (registered < 3) {
            proxy.register();
            registered++;
        }
        final long shutdownDeadline = 10;
        final long duration = 10;
        final int maxRestarts = 10;
        supervisor.addChild(new ChildSpec (
            posAcct.getName(), mode,
            maxRestarts, duration, durationUnit,
            shutdownDeadline, proxyRef
        ));
        final PosAcct pa = (PosAcct) proxyRef;
        posAccts.put(posAcct.getName(), pa);
        return pa;
    }

    /**
     * Creates a new ActorManager
     */
    private ActorManager() {
        supervisor = new SupervisorActor("PosAcctSupervisor", RestartStrategy.ONE_FOR_ONE).spawn();
        log("Supervisor Started");
    }
}
