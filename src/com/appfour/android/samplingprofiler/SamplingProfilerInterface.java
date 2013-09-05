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

import java.io.*;

import dalvik.system.profiler.*;

public final class SamplingProfilerInterface
{
	private static final Object lock = new Object();
	private static SamplingProfiler samplingProfiler;

	private SamplingProfilerInterface()
	{
	}
	
	public static boolean isSampling()
	{
		synchronized (lock)
		{
			return samplingProfiler != null;
		}
	}

	public static void startSampling(int stackDepth, int intervalInMs, final ThreadSet threadSet)
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
			if (samplingProfiler != null)
			{
				throw new IllegalStateException("Sampling profiler already started");
			}
			samplingProfiler = new SamplingProfiler(stackDepth, 
			new SamplingProfiler.ThreadSet()
			{
				@Override
				public Thread[] threads()
				{
					return threadSet.threadsToSample();
				}
			});
			samplingProfiler.start(intervalInMs);
			
		}
	}

	public static void startSampling(int stackDepth, int intervalInMs)
	{
		startSampling(stackDepth, intervalInMs, new AllThreadsThreadSet());
	}

	public static void startSampling(int stackDepth, int intervalInMs, final Thread... threads)
	{
		if (threads == null || threads.length == 0)
		{
			throw new IllegalArgumentException();
		}

		startSampling(stackDepth, intervalInMs, new ThreadSet()
		{
			@Override
			public Thread[] threadsToSample()
			{
				return threads;
			}
		});
	}

	public static void finishSampling(OutputStream hprofOutStream) throws IOException
	{
		HprofData hprofData;
		synchronized (lock)
		{
			if (samplingProfiler == null)
			{
				throw new IllegalStateException("Sampling profiler not started");
			}
			samplingProfiler.stop();
			samplingProfiler.shutdown();
			hprofData = samplingProfiler.getHprofData();
			samplingProfiler = null;
		}
		AsciiHprofWriter.write(hprofData, hprofOutStream);
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
				while(newLen < activeCount)
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
					for(int i = lastEnumeratedCount; i < newLen; i++)
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
