Android Sampling Profiler Facade
======================

There is a hidden sampling profiler in Android going back at least as far as Android 2.3. It is not
supported by the Android development tools and it is an in-process profiler which can't be triggered from
the Dalvik Debug Monitor Server (DDMS). Furthermore it is not easily discoverable because it is not included 
in the ADT android.jar files against which apps are built. 

To make it easy to use the sampling profiler we have created an small library, the Android Sampling 
Profiler Facade. It works with Android 2.3 and Android 4.x. To use just drop it into the libs/ directory
of your Android project. If your project is an Android Studio project you also have to add the JAR dependency
to your `build.gradle` file:

``` c
dependencies {
    compile files('libs/sampling-profiler-facade.jar')
}
```

Since the Android sampling profiler is in-process you need to add some code to your app. The public API of
the facade is contained in `com.appfour.samplingprofiler.SamplingProfilerfacade`.

First initialize the profiler, e.g. in on the `onCreate()` method of an activity. The init() methods take
some parameters which can not be changed for this profiling session:

* `stackDepth` - specifies number of stackframes that are being captured during
  profiling. With a higher number more of the callchain to hotspots will be visible when
  analyzing the captured data later. But a higher stack depth also requires more 
  memory on the VM heap during profiling. A stack depth of ten is a good number 
  to start with.
  
* `intervalInMs` - specifies the interval between samples taken in ms. The lower the
  number the higher the resolution. But a lower number also means that the sampling profiler
  requires more memory and affects the behavior of the program more. An interval of 10 ms 
  (equivalent to about 100 samples per second) is a good value to start with.
 
* The third parameter specifies what threads are being sampled. There are several methods for specifying all
  thread, a fixed number of threads, or a dynamically computed ThreadSet
  
Sampling can be started and stopped using the `startSampling()` and `stopSampling()` methods. This way 
only the interesting parts of the app are being profiled.

Once all interesting parts have been profiled the profiler can be shut down and profiling data can be 
written out with the writeHprofDataAndShutdown() method.

This example shows how take start samples when the activity is started and how to stop taking samples
and write the profiling data once the activity is left.

``` java
    @Override
    protected void onStart() {
        super.onStart();
        SamplingProfilerFacade.init(10, 10, Thread.currentThread());
        SamplingProfilerFacade.startSampling();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SamplingProfilerFacade.stopSampling();
        File outFile = new File(Environment.getExternalStorageDirectory(), "sampling.hprof");
        try {
            FileOutputStream outStream = new FileOutputStream(outFile);
            try {
                SamplingProfilerFacade.writeHprofDataAndShutdown(outStream);
            } finally {
                outStream.close();
            }
        } catch (IOException e) {
            Log.e("Sampling", "I/O exception writing sampling profiler data", e);
        }
    }
```

Analyzing the profiling data
-------
The profiling data is written out in HPROF format. The only tool I have found which can analyzse
those files is [JPerfAnal](http://jperfanal.sourceforge.net/). It looks a bit outdated and 
does not have a lot of features but it works well enough for finding hotspots.
After copying the .hprof file onto your PC run JPerfAnal with `java -jar jperfanal.jar sampling.hprof`.

The line number information capture does not seem to be accurate so the 'Method times by Caller' and
'Method times by callee' sections are the most interesting.
