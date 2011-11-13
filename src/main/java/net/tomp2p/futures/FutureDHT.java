/*
 * Copyright 2009 Thomas Bocek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.tomp2p.futures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import net.tomp2p.p2p.EvaluatingSchemeDHT;
import net.tomp2p.p2p.VotingSchemeDHT;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;

import org.jboss.netty.buffer.ChannelBuffer;

public class FutureDHT extends BaseFutureImpl
{
	final private int min;
	final private EvaluatingSchemeDHT evaluationScheme;
	final private FutureCreate<FutureDHT> futureCreate;
	final private FutureRouting futureRouting;
	private Map<PeerAddress, Collection<Number160>> rawKeys;
	private Map<PeerAddress, Map<Number160, Data>> rawData;
	private Map<PeerAddress, Object> rawObjects;
	private Map<PeerAddress, ChannelBuffer> rawChannels;
	//
	private List<FutureResponse> pendingFutures=new ArrayList<FutureResponse>(4);
	//
	private ScheduledFuture<?> scheduledFuture;
	private List<ScheduledFuture<?>> scheduledFutures;
	private boolean cancelSchedule = false;
	//
	private boolean minReached;

	public FutureDHT()
	{
		this(0, new VotingSchemeDHT(), null, null);
	}

	public FutureDHT(final int min, final EvaluatingSchemeDHT evaluationScheme, FutureCreate<FutureDHT> futureCreate,
			FutureRouting futureRouting)
	{
		this.min = min;
		this.evaluationScheme = evaluationScheme;
		this.futureCreate = futureCreate;
		this.futureRouting = futureRouting;
	}

	public void created(FutureDHT futureDHT)
	{
		if (futureCreate != null) futureCreate.repeated(futureDHT);
	}

	public void setRemovedKeys(final Map<PeerAddress, Collection<Number160>> rawKeys)
	{
		synchronized (lock)
		{
			if (!setCompletedAndNotify()) return;
			this.rawKeys = rawKeys;
			this.minReached = rawKeys.size() >= min;
			this.type = minReached ? FutureType.OK : FutureType.FAILED;
			this.reason = minReached ? "Minimun number of results reached" : "Expected "+min+" result, but got "+rawKeys.size();
		}
		notifyListerenrs();
	}

	public void setStoredKeys(final Map<PeerAddress, Collection<Number160>> rawKeys, boolean ifAbsent)
	{
		synchronized (lock)
		{
			if (!setCompletedAndNotify()) return;
			this.rawKeys = rawKeys;
			this.minReached = rawKeys.size() >= min;
			// we cannot report a failure for store if absent, because a value
			// may be present and we did not store anything, which is ok.
			if (ifAbsent)
			{
				this.type = FutureType.OK;
				this.reason = "ok, since store if absent";
			}
			// if put returns 0 keys means failure, we wanted to store values,
			// but did not
			else
			{
				this.type = FutureType.FAILED;
				this.reason = "Key size is zero";
				for (Collection<Number160> result : rawKeys.values())
				{
					if (result.size() > 0)
					{
						this.type = FutureType.OK;
						this.reason = "size ok";
						break;
					}
				}
			}
		}
		notifyListerenrs();
	}

	public void setData(final Map<PeerAddress, Map<Number160, Data>> rawData)
	{
		synchronized (lock)
		{
			if (!setCompletedAndNotify()) return;
			this.rawData = rawData;
			this.minReached = rawData.size() >= min;
			this.type = minReached ? FutureType.OK : FutureType.FAILED;
			this.reason = minReached ? "Minimun number of results reached" : "Expected "+min+" result, but got "+rawData.size();
		}
		notifyListerenrs();
	}

	public Map<PeerAddress, Collection<Number160>> getRawKeys()
	{
		synchronized (lock)
		{
			return rawKeys;
		}
	}

	public Collection<Number160> getKeys()
	{
		synchronized (lock)
		{
			return evaluationScheme.evaluate1(rawKeys);
		}
	}

	public Map<PeerAddress, Map<Number160, Data>> getRawData()
	{
		synchronized (lock)
		{
			return rawData;
		}
	}

	public Map<Number160, Data> getData()
	{
		synchronized (lock)
		{
			return evaluationScheme.evaluate2(rawData);
		}
	}

	public boolean isMinReached()
	{
		synchronized (lock)
		{
			return minReached;
		}
	}

	public void setDirectData1(Map<PeerAddress, ChannelBuffer> rawChannels)
	{
		synchronized (lock)
		{
			if (!setCompletedAndNotify()) return;
			this.rawChannels = rawChannels;
			this.minReached = rawChannels.size() >= min;
			this.type = minReached ? FutureType.OK : FutureType.FAILED;
			this.reason = minReached ? "Minimun number of results reached" : "Expected "+min+" result, but got "+rawData.size();
		}
		notifyListerenrs();
	}

	public Map<PeerAddress, ChannelBuffer> getRawDirectData1()
	{
		synchronized (lock)
		{
			return rawChannels;
		}
	}

	public void setDirectData2(Map<PeerAddress, Object> rawObjects)
	{
		synchronized (lock)
		{
			if (!setCompletedAndNotify()) return;
			this.rawObjects = rawObjects;
			this.minReached = rawObjects.size() >= min;
			this.type = minReached ? FutureType.OK : FutureType.FAILED;
			this.reason = minReached ? "Minimun number of results reached" : "Expected "+min+" result, but got "+rawData.size();
		}
		notifyListerenrs();
	}

	public Map<PeerAddress, Object> getRawDirectData2()
	{
		synchronized (lock)
		{
			return rawObjects;
		}
	}

	public FutureCreate<FutureDHT> getFutureCreate()
	{
		synchronized (lock)
		{
			return futureCreate;
		}
	}

	/**
	 * Returns the future object that was used for the routing. Before the
	 * FutureDHT is used, FutureRouting has to be completed successfully.
	 * 
	 * @return The future object during the previous routing, or null if routing
	 *         failed completely.
	 */
	public FutureRouting getFutureRouting()
	{
		synchronized (lock)
		{
			return futureRouting;
		}
	}

	public void setScheduledFuture(ScheduledFuture<?> scheduledFuture, List<ScheduledFuture<?>> scheduledFutures)
	{
		synchronized (lock)
		{
			this.scheduledFuture = scheduledFuture;
			this.scheduledFutures = scheduledFutures;
			if (cancelSchedule == true) cancel();
		}
	}

	@Override
	public void cancel()
	{
		synchronized (lock)
		{
			cancelSchedule = true;
			if (scheduledFuture != null) scheduledFuture.cancel(false);
			if (scheduledFutures != null) scheduledFutures.remove(scheduledFuture);
		}
		super.cancel();
	}

	public Object getObject()
	{
		synchronized (lock)
		{
			return this.evaluationScheme.evaluate3(rawObjects);
		}
	}

	public Object getChannelBuffer()
	{
		synchronized (lock)
		{
			return this.evaluationScheme.evaluate4(rawChannels);
		}
	}

	public void addPending(FutureResponse futureResponse) 
	{
		synchronized (lock)
		{
			pendingFutures.add(futureResponse);
		}
	}
	
	/**
	 * Returns back those futures that are still running. If 6 storage futures are started at the same time and
	 * 5 of them finish, and we specified that we are fine if 5 finishes, then futureDHT returns success. However, the
	 * future that may still be running is the one that stores the content to the closest peer. For testing this is not 
	 * acceptable, thus after waiting for futureDHT, one needs to wait for the running futures as well.
	 *  
	 * @return A future that finishes if all running futures are finished.
	 */
	public FutureForkJoin<FutureResponse> getRunningFutures()
	{
		synchronized (lock)
		{
			final int size=pendingFutures.size();
			final FutureResponse[] futureResponses=new FutureResponse[size];
			for(int i=0;i<size;i++)
			{
				futureResponses[i]=pendingFutures.get(i);
			}
			return new FutureForkJoin<FutureResponse>(futureResponses);
		}
	}
}
