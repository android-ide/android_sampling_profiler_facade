/*
 * Copyright (C) 2013 appfour GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appfour.android.samplingprofiler;

import java.io.IOException;
import java.io.OutputStream;

public final class SamplingProfilerFacade
{
	private static final Object lock = new Object();
	private static SamplingProfilerAdapter samplingProfilerAdapter;
	private static int intervalInMs;
	private static boolean sampling;

	private SamplingProfilerFacade()
	{
	}

	public static boolean isSampling()
	{
		synchronized (lock)
		{
			return sampling;
		}
	}

	public static void init(int stackDepth, int intervalInMs, final ThreadSet threadSet)
	{
		synchronized (lock)
		{
			if (threadSet == null)
			{
				throw new IllegalArgumentException();
			}
			if (intervalInMs <= 0)
			{
				throw new IllegalArgumentException();
			}
			if (stackDepth <= 0)
			{
				throw new IllegalArgumentException();
			}
			if (samplingProfilerAdapter != null)
			{
				throw new IllegalStateException("Sampling profiler already initialized");
			}
			samplingProfilerAdapter = new IcsSamplingProfilerAdapter();
			samplingProfilerAdapter.init(stackDepth, threadSet);
			SamplingProfilerFacade.intervalInMs = intervalInMs;
		}
	}

	public static void init(int stackDepth, int intervalInMs, final Thread... threads)
	{
		if (threads == null || threads.length == 0)
		{
			throw new IllegalArgumentException();
		}

		init(stackDepth, intervalInMs, new ThreadSet()
		{
			@Override
			public Thread[] threadsToSample()
			{
				return threads;
			}
		});
	}

	public static void init(int stackDepth, int intervalInMs)
	{
		init(stackDepth, intervalInMs, new AllThreadsThreadSet());
	}

	public static void startSampling()
	{
		synchronized (lock)
		{
			if (samplingProfilerAdapter == null)
			{
				throw new IllegalStateException("Sampling profiler not initialized");
			}
			if (sampling)
			{
				throw new IllegalStateException("Sampling profiler already started");
			}
			samplingProfilerAdapter.start(intervalInMs);
			sampling = true;
		}
	}

	public static void stopSampling()
	{
		synchronized (lock)
		{
			if (samplingProfilerAdapter == null)
			{
				throw new IllegalStateException("Sampling profiler not initialized");
			}
			if (!sampling)
			{
				throw new IllegalStateException("Sampling profiler not started");
			}
			samplingProfilerAdapter.stop();
			sampling = false;
		}
	}

	public static void writeHprofDataAndShutdown(OutputStream hprofOutStream) throws IOException
	{
		synchronized (lock)
		{
			if (samplingProfilerAdapter == null)
			{
				throw new IllegalStateException("Sampling profiler not started");
			}
			samplingProfilerAdapter.stop();
			samplingProfilerAdapter.shutdown();
			samplingProfilerAdapter.writeHprofData(hprofOutStream);
			samplingProfilerAdapter = null;
		}
	}

	public static interface ThreadSet
	{
		public Thread[] threadsToSample();
	}

	private static class AllThreadsThreadSet implements ThreadSet
	{
		private Thread[] threads;
		private int lastEnumeratedCount;

		public AllThreadsThreadSet()
		{
			threads = new Thread[32];
			lastEnumeratedCount = threads.length;
		}

		@Override
		public Thread[] threadsToSample()
		{
			// repeat until all active threads have been captured
			while (true)
			{
				int activeCount = Thread.activeCount();
				int threadsLen = threads.length;
				int newLen = threadsLen;
				while (newLen < activeCount)
				{
					newLen *= 2;
				}
				if (newLen != threadsLen)
				{
					threads = new Thread[newLen];
				}
				int enumeratedCount = Thread.enumerate(threads);
				if (enumeratedCount <= newLen)
				{
					for (int i = lastEnumeratedCount; i < newLen; i++)
					{
						threads[i] = null;
					}
					lastEnumeratedCount = enumeratedCount;
					return threads;
				}
			}
		}
	}
}
