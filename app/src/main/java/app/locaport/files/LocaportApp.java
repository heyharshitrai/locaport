package app.locaport.files;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class LocaportApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
