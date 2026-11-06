package com.tanzir.javario;

import android.app.Application;

public class JavarioApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
    }
}
