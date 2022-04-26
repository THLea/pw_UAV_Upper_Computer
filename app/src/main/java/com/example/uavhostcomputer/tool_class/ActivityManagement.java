package com.example.uavhostcomputer.tool_class;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

public class ActivityManagement {
    private static final List<Activity> activities = new ArrayList<>();

    public static void addActivity(Activity activity){
        activities.add(activity);
    }

    public static void removeActivity(Activity activity){
        activities.remove(activity);
    }

    public static void finishAll(){
        for(Activity i : activities){
            if(!i.isFinishing()){
                i.finish();
            }
        }
    }
}
