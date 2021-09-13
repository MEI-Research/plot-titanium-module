package com.meiresearch.android.plotprojects;

public class EMAFilterRegion {

    private static final String CUSTOM_HEALTHKICK = "custom_healthkick";

    // return true if region is allowed.
    // return false if region isn't allowed.
    // this is designed for a corner case with healthkick to filter out some combination region names.
    public static boolean regionAllowed(String region_name){

        if(region_name.toLowerCase().indexOf("generic,") == 0){
            return true;
        }

        // disallow the TestGrid from messing up the normal operation of the module until we figure it out.
        if(region_name.toLowerCase().indexOf("testgrid") >= 0){
            return false;
        }

        String pp_val = EMADataAccess.getStringProperty("plotProjects.project");
        if(pp_val.equals(CUSTOM_HEALTHKICK)){
            return healthkickFilter(region_name);
        }

        return true;
    }

    // this is a custom function only for STTR healthkick but we don't have an easy mechanism
    // to include 'custom' code in these native modules.
    // this whitelists only certain regions and disallows all others.
    private static boolean healthkickFilter(String region_name){

        if(region_name.toLowerCase().contains("tacobell")){
            return true;
        } else if(region_name.toLowerCase().contains("wendys")){
            return true;
        } else if(region_name.toLowerCase().contains("subway")){
            return true;
        } else if(region_name.toLowerCase().contains("mcdonalds")){
            return true;
        } else if(region_name.toLowerCase().contains("burgerking")){
            return true;
        } else if(region_name.toLowerCase().contains("kfc")){
            return true;
        }

        return false;

        // switch(region_name.toLowerCase()){
        //     case "[tacobell]":
        //     case "[wendys]":
        //     case "[subway]":
        //     case "[mcdonalds]":
        //     case "[burgerking]":
        //     case "[kfc]":
        //         return true;
        //     default:
        //         return false;
        // }
    }
}
