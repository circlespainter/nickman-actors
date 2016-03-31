/**
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
 */
package com.heliosapm.actors;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.utils.io.StdInCommandHandler;
import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.time.SystemClock;
import com.heliosapm.utils.time.SystemClock.ElapsedTime;

import co.paralleluniverse.actors.Actor;
import co.paralleluniverse.actors.ActorBuilder;
import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.behaviors.ProxyServerActor;
import co.paralleluniverse.actors.behaviors.Supervisor;
import co.paralleluniverse.actors.behaviors.Supervisor.ChildMode;
import co.paralleluniverse.actors.behaviors.Supervisor.ChildSpec;
import co.paralleluniverse.actors.behaviors.SupervisorActor;
import co.paralleluniverse.actors.behaviors.SupervisorActor.RestartStrategy;
import co.paralleluniverse.fibers.SuspendExecution;


/**
 * <p>Title: ActorManager</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.actors.ActorManager</code></p>
 */

public class ActorManager {
	private static volatile ActorManager instance = null;
	private static final Object lock = new Object();
	
	protected final Supervisor supervisor;
	protected ChildMode mode = ChildMode.PERMANENT;
	protected int maxRestarts = 10;
	protected long duration = 10;
	protected TimeUnit durationUnit = TimeUnit.SECONDS;
	protected long shutdownDeadline = 10;
	
	protected final NonBlockingHashMap<String, PosAcct> posAccts = new NonBlockingHashMap<String, PosAcct>(); 

	/** Static class logger */
	private static final Logger LOG = LoggerFactory.getLogger(ConnectionPool.class);

	public static ActorManager getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ActorManager();
				}
			}
		}
		return instance;
	}
	
	public static void main(String[] args) {
		LOG.info("PosAcct Test");
		ActorManager am = getInstance();
		long seq = ConnectionPool.getInstance().getSQLWorker().nextSeq("POSACCT_SEQ");
		for(int i = 0; i < 10; i++) {
			am.newPosAcct("PosAcct#" + seq, 1);
			seq++;
		}
		LOG.info("Created 10 new accounts");
		JMXHelper.fireUpJMXMPServer(9999);
		StdInCommandHandler.getInstance().registerCommand("dep", new Runnable(){
			public void run() {
				int x = 0;
				final ElapsedTime et = SystemClock.startClock();
				for(int i = 0; i < 100; i++) {
					x += depositAll();
				}
				LOG.info(et.printAvg("Deposits", x));
			}
		});
		StdInCommandHandler.getInstance().registerCommand("blowup", new Runnable(){
			public void run() {
				PosAcct pa = getInstance().posAccts.values().iterator().next();
				LOG.info("Blowing Up: [{}] : [{}]", pa, ((ActorRef)pa).getName());
				try {
					pa.blowUp();
				} catch (Throwable ex) {
					ex.printStackTrace(System.err);
				}
				
			}
		});
		
		StdInCommandHandler.getInstance().run();
		
		
	}
	
	protected static int depositAll() {
		ActorManager am = getInstance();
//		final ElapsedTime et = SystemClock.startClock();
		int x = 0;
		for(PosAcct pa: am.posAccts.values()) {
			pa.deposit(new BigDecimal(1));
			x++;
		}
//		LOG.info("{}", et.printAvg("Deposit", x));
		return x;
	}
	
	protected Actor loadPosAcct(final long id) {
		final PosAcct posAcct = PosAcctImpl.load(id);
		return new ProxyServerActor(
				false, 		
				posAcct,
				PosAcct.class
		);		
	}
	
	protected ActorBuilder builder(final long id) {
		return new ActorBuilder() {
			@Override
			public Actor build() throws SuspendExecution {				
				return loadPosAcct(id);
			}
		};
	}
	
	protected PosAcct register(final PosAcct posAcct) {
		@SuppressWarnings("resource")		
		final ProxyServerActor proxy = new ProxyServerActor(
				false, 		
				posAcct,
				PosAcct.class
		);		
		
		final ActorRef<?> actorRef = proxy.spawn();
		posAcct.setActorRef(actorRef);
		try {
			proxy.register(posAcct.getName());
			supervisor.addChild(new ChildSpec(
					posAcct.getName(), mode,
					maxRestarts, duration, durationUnit,
					shutdownDeadline, actorRef
			));
		} catch (SuspendExecution e) {
			// Should not go here
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		PosAcct pa = (PosAcct)actorRef;
		posAccts.put(posAcct.getName(), pa);
		return pa;
		
	}
	
	public PosAcct newPosAcct(final String name, final Number balance) {
		return register(new PosAcctImpl(name, new BigDecimal(balance.toString()))); 
	}
	
	
	/**
	 * Creates a new ActorManager
	 */
	@SuppressWarnings("resource")
	private ActorManager() {
		supervisor = new SupervisorActor("PosAcctSupervisor", RestartStrategy.ONE_FOR_ONE).spawn();		
		LOG.info("Supervisor Started. Loading Actors...");
		final Iterator<PosAcct> posIter = PosAcctImpl.load();
		int loaded = 0;
		while(posIter.hasNext()) {
			register(posIter.next());
			loaded++;			
		}
		LOG.info("Loaded [{}] Actors", loaded);
	}

}
