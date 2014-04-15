package com.renaud.yoscamper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.commonsware.cwac.wakeful.WakefulIntentService;

public class OnAlarmReceiver extends BroadcastReceiver 
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		Intent intentService = new Intent(context, YosSearchService.class);
		// copy the extras from the initial intent so that we can pass all the parameters to the service:
		intentService.putExtras(intent); 
		
		WakefulIntentService.sendWakefulWork(context, intentService);
	}
}
