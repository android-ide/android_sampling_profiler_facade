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

package dalvik.system.profiler;

/**
 * Stub interface for compilation only. Compatible with the Android ICS and JB
 * sampling profiler implementation.
 */

public class SamplingProfiler
{
	public SamplingProfiler(int depth, ThreadSet threadSet)
	{
	}

	public void start(int interval)
	{
	}

	public void stop()
	{
	}

	public void shutdown()
	{
	}

	public HprofData getHprofData()
	{
		return null;
	}

	public static interface ThreadSet
	{
		public Thread[] threads();
	}
}
