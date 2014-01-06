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

/**
 * Facade to Android's built-in hidden sampling profiler. The facade is necessary
 * because the sampling profiler API is include in the Android SDK's android.jar
 * and the API has changed considerably between Gingerbread and KitKat.
 * 
 * To use first initialize the facade with one of the {@code init()} methods.
 * Afterwards once the application enters a phase which you want to profile call
 * the {@code startProfiling()} method. Then sampling can be paused with 
 * {@code stopProfiling()}. Once all data is captured the data can be written with
 * {@code writeHprofDataAndShutdown()}. If necessary the capture process can be started
 * again with {@code init()}.
 */
public final class SamplingProfilerFacade
{
	private static final Object lock = new Object();
	private static SamplingProfilerAdapter samplingProfilerAdapter;
	private static int intervalInMs;
	private static boolean sampling;

	private SamplingProfilerFacade()
	{
	}

	/**
     * Returns true if the sampling profiler is currently recording samples.
	 */
	public static boolean isSampling()
	{
		synchronized (lock)
		{
			return sampling;
		}
	}

	/**
	 * Initializes the sampling profiler. Needs to be called before profiling is started.
	 * 
	 * The {@code stackDepth} specifies number of stackframes that are being captured during
	 * profiling. With a higher number more of the callchain to hotspots will be visible when
	 * analyzing the captured data later. But a higher stack depth also requires more 
	 * memory on the VM heap during profiling. A stack depth of ten is a good number 
	 * to start with.
	 * 
	 * {@code intervalInMs} specifies the interval between samples taken in ms. The lower the
	 * number the higher the resolution. But a lower number also means that the sampling profiler
	 * requires more memory and affects the behavior of the program more. An interval of 10 ms 
	 * (equivalent to about 100 samples per second) is a good value to start with.
	 * 
	 * The {@code threadsToSample} method of the supplied {@code threadSet} is called each 
	 * time a sample is taken and tells the profiler which threads to include in the sample.
	 * Should be limited to interesting threads.
	 * 
	 * @throws UnsupportedOperationException on unsupported Android versions
	 */
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
			switch (android.os.Build.VERSION.SDK_INT)
			{
			case android.os.Build.VERSION_CODES.GINGERBREAD:
			case android.os.Build.VERSION_CODES.GINGERBREAD_MR1:
				samplingProfilerAdapter = new GingerbreadSamplingProfilerAdapter();
				break;
			case android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH:
			case android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1:
			case android.os.Build.VERSION_CODES.JELLY_BEAN:
			case android.os.Build.VERSION_CODES.JELLY_BEAN_MR1:
			case android.os.Build.VERSION_CODES.JELLY_BEAN_MR2:
			case android.os.Build.VERSION_CODES.KITKAT:
				samplingProfilerAdapter = new IcsSamplingProfilerAdapter();
				break;
			default:
				throw new UnsupportedOperationException("API level "+android.os.Build.VERSION.SDK_INT+" not supported by sampling profiler facade.");
			}
			samplingProfilerAdapter.init(stackDepth, threadSet);
			SamplingProfilerFacade.intervalInMs = intervalInMs;
		}
	}

	/**
	 * Initializes the sampling profiler. Needs to be called before profiling is started.
	 * 
	 * The {@code stackDepth} specifies number of stackframes that are being captured during
	 * profiling. With a higher number more of the callchain to hotspots will be visible when
	 * analyzing the captured data later. But a higher stack depth also requires more 
	 * memory on the VM heap during profiling. A stack depth of ten is a good number 
	 * to start with.
	 * 
	 * {@code intervalInMs} specifies the interval between samples taken in ms. The lower the
	 * number the higher the resolution. But a lower number also means that the sampling profiler
	 * requires more memory and affects the behavior of the program more. An interval of 10 ms 
	 * (equivalent to about 100 samples per second) is a good value to start with.
	 * 
	 * Only stacktraces of the supplied {@code threads} are included in the sample.
	 */
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

	/**
	 * Initializes the sampling profiler. Needs to be called before profiling is started.
	 * 
	 * The {@code stackDepth} specifies number of stackframes that are being captured during
	 * profiling. With a higher number more of the callchain to hotspots will be visible when
	 * analyzing the captured data later. But a higher stack depth also requires more 
	 * memory on the VM heap during profiling. A stack depth of ten is a good number 
	 * to start with.
	 * 
	 * {@code intervalInMs} specifies the interval between samples taken in ms. The lower the
	 * number the higher the resolution. But a lower number also means that the sampling profiler
	 * requires more memory and affects the behavior of the program more. An interval of 10 ms 
	 * (equivalent to about 100 samples per second) is a good value to start with.
	 * 
	 * All live threads are sampled.
	 */
	public static void init(int stackDepth, int intervalInMs)
	{
		init(stackDepth, intervalInMs, new AllThreadsThreadSet());
	}

	/**
	 * Starts the sampling profiler. After being started samples are being taken until
	 * the sampling profiler is stopped with {@code stopSampling()} or shut down.
	 * 
	 * The sampling profiler needs to be initialized via one of the {@code init()} methods 
	 * first.
	 */
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

	/**
	 * Stops the sampling profiler but does not erase already taken samples. Sampling can
	 * be resumed again afterwards with {@code startSampling()}.
	 */
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

	/**
	 * Stops the sampling profiler first if necessary. Writes data about the already taken 
	 * samples in HPROF format to the provided {@code hprofOutStream}. 
	 * 
	 * Also erases the samples in memory and shuts down the profiler.
	 */
	public static void writeHprofDataAndShutdown(OutputStream hprofOutStream) throws IOException
	{
		synchronized (lock)
		{
			if (samplingProfilerAdapter == null)
			{
				throw new IllegalStateException("Sampling profiler not started");
			}
			if (sampling)
			{
				samplingProfilerAdapter.stop();
			}
			samplingProfilerAdapter.shutdown();
			samplingProfilerAdapter.writeHprofData(hprofOutStream);
			samplingProfilerAdapter = null;
		}
	}

	public static interface ThreadSet
	{
		/**
		 * This method is called each time a sample is taken and tells the profiler which threads 
		 * to include in the sample. Should be limited to interesting threads.
		 */
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
