package com.yusdesign.valence;

import android.app.Application;
import android.util.Log;

public class ValenceApplication extends Application {
    @Override
    public void onCreate() {
        // This is the VERY FIRST thing that runs
        Log.e("ValenceApp", "=========================================");
        Log.e("ValenceApp", "ValenceApplication: onCreate() STARTED");
        Log.e("ValenceApp", "=========================================");
        
        super.onCreate();
        
        Log.e("ValenceApp", "ValenceApplication: onCreate() COMPLETED");
    }
}
