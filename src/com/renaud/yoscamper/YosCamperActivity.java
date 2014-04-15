package com.renaud.yoscamper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;

public class YosCamperActivity extends Activity
{
	private static final String TAG = "YosCamperActivity";
	
	private TextView m_textViewResults;
	private TextView m_textViewDate;
	private TextView m_textViewSelectedCampgrounds;
    private Button m_buttonSelectDate;
    private Button m_buttonSearch;
    private Button m_buttonStop;
    private Button m_buttonSelectCampgrounds;
    private EditText m_editTextNumNights;
    private EditText m_editTextSearchFreq;
    private EditText m_editTextSearchDuration;
    
    private int m_year;
    private int m_month;
    private int m_day;
    private long m_startTime;
    private int m_searchFreq;
    private int m_searchDuration;
    private int m_numAttempts;
    
    private boolean m_activityIsInFront;
    private boolean m_searchInProgress;
    
    static final int DATE_DIALOG_ID = 0;
    
    
    String[] m_campsList = { "North Pines", "Lower Pines", "Upper Pines", "Crane Flat", "Hogdon Meadow", "Wawona", "Tuolumne Meadows" };
	boolean[] m_campsSelection =  new boolean[ m_campsList.length ];
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        m_activityIsInFront = true;
        
        // initialize campgrounds selection: 
        for (int i=0; i<m_campsSelection.length; i++)
        	m_campsSelection[i] = true;
		
        m_textViewResults = (TextView) findViewById(R.id.textViewResults);
		m_textViewDate = (TextView) findViewById(R.id.textViewDate);
		m_textViewSelectedCampgrounds = (TextView) findViewById(R.id.textViewSelectedCampgrounds);
        m_buttonSelectDate = (Button) findViewById(R.id.buttonSelectDate);
        m_buttonSearch = (Button) findViewById(R.id.buttonSearch);
        m_buttonSelectCampgrounds = (Button) findViewById(R.id.buttonSelectCampgrounds);
        m_buttonStop = (Button) findViewById(R.id.buttonStop);
        m_editTextNumNights = (EditText) findViewById(R.id.editTextNumNights);
        m_editTextSearchFreq = (EditText) findViewById(R.id.editTextSearchFreq);
        m_editTextSearchDuration = (EditText) findViewById(R.id.editTextSearchDuration);
        
        // add a click listener to the select date button:
        m_buttonSelectDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(DATE_DIALOG_ID);
            }
        });
        
        // add a click listener to the search button:
        m_buttonSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	startSearch();
            }
        });
        
        // add a click listener to the select campgrounds button:
        m_buttonSelectCampgrounds.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	selectCampgrounds();
            }
        });
        
        // add a click listener to the stop button:
        m_buttonStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	m_textViewResults.setText("Search cancelled.");
            	stopSearch();
            }
        });
        
        // get the current date:
        final Calendar c = Calendar.getInstance();
        m_year = c.get(Calendar.YEAR);
        m_month = c.get(Calendar.MONTH);
        m_day = c.get(Calendar.DAY_OF_MONTH);
        // display the current date:
        updateDateDisplay();
        
        m_searchInProgress = false;
    }
    
    @Override
    protected void onResume()
    {
    	// keep track of whether the activity is in front or not:
    	m_activityIsInFront = true;
    	super.onResume();
    }
    
    @Override
    protected void onPause() 
	{
    	// keep track of whether the activity is in front or not:
    	m_activityIsInFront = false;
    	super.onPause();
    }
    
    // handle the messages sent from the YosSearchService:
    private Handler m_handlerServiceMsg = new Handler()
    {
    	@Override
        public void handleMessage(Message msg)
    	{
    		Bundle msgData = msg.getData();
    		// get the msg type:
    		String msgType = msgData.getString("type");
    		// process the msg accordingly:
    		if (msgType == "results")
    		{
    			Bundle results = msgData.getBundle("results");
    			parseSearchResults(results, true);
    		}
    		else if (msgType == "startService")
    		{
    			m_textViewResults.setText(String.format("Attempt #%d: Checking reservations server...", m_numAttempts));
    		}
    		else if (msgType == "noConnection")
    		{
    			parseSearchResults(null, false);
    		}
        }
    };
    
    private void parseSearchResults(Bundle results, boolean bConnected)
    {
    	StringBuilder sites = new StringBuilder();
    	boolean sitesAvail = false;
    	
    	// only look at the results if we're connected:
    	if (bConnected)
    	{
	    	// iterate over all the keys (campground names) in the bundle:
	    	Set<String> campgrounds = results.keySet();
	    	
	        for (String campground : campgrounds)
	        {        	
	        	// match the campground name with the selection: 
	        	for (int i=0; i<m_campsList.length; i++)
	        	{ 
	        		if (m_campsSelection[i] && campground.equalsIgnoreCase(m_campsList[i]))
	        		{
	        			// get the number of available sites for the current campground:
	                	int numAvail = results.getInt(campground);
						// append campground's name and number of sites available to the result string:
	                	sites.append(m_campsList[i] + ":\n");
	                	sites.append(numAvail + " site(s) available\n");
	        			sitesAvail = true;
	        		}
	        	}
	        }
    	}
        
        String strResults;
        
        if (sitesAvail) // we found some sites:
        {
        	strResults = sites.toString();
        }
        else // no sites found
        {
        	if (bConnected)
        		strResults = "No sites available for selected campgrounds.\n";
        	else
        		strResults = "Network Connection currently unavailable\n";
        	
        	// if we're doing a repetitive search and if we haven't gone over the search duration, keep going:
        	if (m_searchFreq > 0 &&  m_searchDuration*3600*1000 >= SystemClock.elapsedRealtime() - m_startTime)
        	{
        	   	m_numAttempts++;
        	    // display wait msg until next alarm is triggered:
        	   	strResults += String.format("\nAttempt #%d: waiting for %d minute(s)...", m_numAttempts, m_searchFreq);
        	   	m_textViewResults.setText(strResults);
        	   	// and exit now without stopping the search:
        	   	return;
        	}
        }
        
        // if we got here, stop the search (cancel the alarms if necessary):
        stopSearch();
        
        sendNotification(strResults);
        
	    // add some info to the result string (timestamp and number of attempts):
	    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
	    String currentTime = sdf.format(new Date());
	    strResults += String.format("\nResults obtained at %s after %d attempt(s).\nTotal time spent: %d mins", currentTime, m_numAttempts, m_numAttempts*m_searchFreq);
	    m_textViewResults.setText(strResults);
    }
    
    
    private void sendNotification(String strResults)
    {
    	// send a notification if the activity is not in front:
    	if (m_activityIsInFront)
    		return;
    
    	NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    	// create the notification:
    	Notification notification = new Notification(android.R.drawable.star_big_on, "YosCamper notification", System.currentTimeMillis());
    	// cancel the notification after it is selected:
    	notification.flags |= Notification.FLAG_AUTO_CANCEL;
    	notification.defaults |= Notification.DEFAULT_SOUND;
    	notification.number += 1;
    	// specify the called Activity:
    	Intent notifIntent = new Intent(YosCamperActivity.this, YosCamperActivity.class);
    	// make sure the activity is not launched if it is already running:
    	notifIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    	PendingIntent activity = PendingIntent.getActivity(YosCamperActivity.this, 0, notifIntent, 0);
    	notification.setLatestEventInfo(YosCamperActivity.this, "YosCamper", strResults, activity);
    	notificationManager.notify(0, notification);
    	// TODO: change the notification/activity so that the activity can be restarted with
    	// the results info in case Android killed the activity 
    }
    
    private void stopSearch()
    {
    	// cancel the alarms
    	if (m_searchInProgress /*&& m_searchFreq > 0*/)
    	{
    		Context context = getApplicationContext();
            AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, OnAlarmReceiver.class);
    		PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, 0);
    		mgr.cancel(pi);
    		m_searchInProgress = false;
    	}    	
    }
    
    private void startSearch()
    {
    	m_textViewResults.setText("");
    	// TODO: check params before making the request: date, num nights, freq, duration:    	
    	int numNights = Integer.parseInt(m_editTextNumNights.getText().toString());
    	m_searchFreq = Integer.parseInt(m_editTextSearchFreq.getText().toString());
    	m_searchDuration = Integer.parseInt(m_editTextSearchDuration.getText().toString());
    	m_numAttempts = 1;
    	// start the timer for the search duration:
    	m_startTime = SystemClock.elapsedRealtime();
    	
    	Context context = getApplicationContext();
        AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, OnAlarmReceiver.class);
        intent.putExtra("messenger", new Messenger(m_handlerServiceMsg));
        intent.putExtra("day", m_day);
        intent.putExtra("month", m_month);
        intent.putExtra("year", m_year);
        intent.putExtra("numNights", numNights);
        
        boolean reqYos = false;
        boolean reqTM = false;
        // check if any of the yosemite campgrounds are selected:
        for (int i=0; i<m_campsList.length-1; i++)
        {
        	if (m_campsSelection[i])
           	{
        		reqYos = true;
        		break;
           	}
        }
        // check if tuolumne campground is selected:
        if (m_campsSelection[m_campsList.length-1])
        	reqTM = true;
        // pass in those flags:
        intent.putExtra("reqYos", reqYos);
        intent.putExtra("reqTM", reqTM);
        // Important: use the FLAG_UPDATE_CURRENT flag to make sure the PendingIntent uses the extras from the new intent:
        PendingIntent pIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        
        m_searchInProgress = true;
        
        if (m_searchFreq == 0) // one-time search
        {
        	// if we're doing a one-time search, schedule an alarm to be triggered just once, starting now:
        	mgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), pIntent);
        }
        else // repetitive search:
        {
			// set a repeating alarm to be triggered every searchFreq minutes, starting now:
        	mgr.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
        					 SystemClock.elapsedRealtime(), // trigger the first alarm now
        					 m_searchFreq*60000, // search frequency in milliseconds
        					 pIntent);
        }
    }
    
    private void updateDateDisplay()
    {
        m_textViewDate.setText(
            new StringBuilder()
            		.append("Selected date: ")
                    // Month is 0 based so add 1
                    .append(m_month + 1).append("-")
                    .append(m_day).append("-")
                    .append(m_year).append(" "));
    }
    
    // the callback received when the user picks a the date in the dialog:
    private DatePickerDialog.OnDateSetListener m_dateSetListener =
    		new DatePickerDialog.OnDateSetListener()
    		{
                public void onDateSet(DatePicker view, int year, int month, int day)
                {
                    m_year = year;
                    m_month = month;
                    m_day = day;
                    updateDateDisplay();
                }
            };
    
    @Override
    protected Dialog onCreateDialog(int id) 
    {
        switch (id) 
        {
        case DATE_DIALOG_ID:
            return new DatePickerDialog(this, m_dateSetListener, m_year, m_month, m_day);
        }
        return null;
    }
    
    private void selectCampgrounds()
    { 
    	AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
    	//dialogBuilder.setTitle("Select campgrounds");
    	
	    dialogBuilder.setMultiChoiceItems(m_campsList, m_campsSelection,
	    		new OnMultiChoiceClickListener() {
	    	        @Override
	    	        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
	    	        	m_campsSelection[which] = isChecked;
	    	        }
	    	    } );
    	
    	/* Do this, if we want to use a custom layout:
    	View view = getLayoutInflater().inflate(R.layout.campgrounds_selection, null);
    	dialogBuilder.setView(view);
    	
    	CheckBox checkBoxNorthPines = (CheckBox) view.findViewById(R.id.checkBoxNorthPines);
    	CheckBox checkBoxLowerPines = (CheckBox) view.findViewById(R.id.checkBoxLowerPines);
    	CheckBox checkBoxUpperPines = (CheckBox) view.findViewById(R.id.checkBoxUpperPines);
    	CheckBox checkBoxCraneFlat = (CheckBox) view.findViewById(R.id.checkBoxCraneFlat);
    	CheckBox checkBoxHogdonMeadow = (CheckBox) view.findViewById(R.id.checkBoxHogdonMeadow);
    	CheckBox checkBoxWawona = (CheckBox) view.findViewById(R.id.checkBoxWawona);
    	CheckBox checkBoxTuolumneMeadows = (CheckBox) view.findViewById(R.id.checkBoxTuolumneMeadows);*/    	
    	
    	dialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener()
    	{
    		public void onClick(DialogInterface dialog, int which)
    		{
    			String strCamps = "Selected campgrounds: ";
    			for(int i=0; i< m_campsList.length; i++)
    			{
    				if (m_campsSelection[i])
    					strCamps += m_campsList[i] + ", ";
       			}
    			
    			// make sure to cut out the last 2 characters (", "):
    			m_textViewSelectedCampgrounds.setText(strCamps.substring(0, strCamps.length()-2));
    		}
    	});

    	// Remember, create doesn't show the dialog
    	AlertDialog dialog = dialogBuilder.create();

    	dialog.show();
    }
    
}