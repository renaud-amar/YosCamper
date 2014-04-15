package com.renaud.yoscamper;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Scanner;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

public class YosSearchService extends WakefulIntentService {
	
	private static final String TAG = "YosSearchService";
	
	public YosSearchService() {
		super("YosSearchService");
	}
	
	// request args for all Yosemite campgrounds:
	private static final String[][] m_requestArgsYos = {
		{ "locationCriteria", "YOSEMITE NATIONAL PARK" },
		{ "locationPosition", "NRSO:2991:-119.583889:37.744444:" },
		{ "interest", "camping" },
		{ "currentMaximumWindow", "12" }
	};
	
	// request args for Tuolumne Campground only:
	// NOTE: somehow the results for the TM campground don't show up correctly on the overall
	// yosemite campgrounds results page, so we need to query it separately to get the right results:
	private static final String[][] m_requestArgsTM = {
		{ "contractCode", "NRSO" },
		{ "parkId", "70926" },
		{ "siteTypeFilter", "ALL" },
		{ "submitSiteForm", "true" },
		{ "search", "site" },
		{ "currentMaximumWindow", "12" }
	};
	
	private static final String m_requestUrlYos = "http://www.recreation.gov/unifSearch.do"; //?topTabIndex=Search";
	//private static final String m_requestUrlYos = "http://www.recreation.gov/unifSearchResults.do?topTabIndex=Search";
	private static final String m_requestUrlTM = "http://www.recreation.gov/campsiteSearch.do";

	@Override
	protected void doWakefulWork(Intent intent) {
		
		Bundle extras = intent.getExtras();
		// TODO: handle that error case:
		if (extras == null)
			return;
		
		Messenger messenger = (Messenger) extras.get("messenger");
		Bundle bundle = new Bundle();
		
		// if the network connection is not currently available, alert the activity:
		if (!isNetworkAvailable())
		{
			bundle.putString("type", "noConnection");
			SendMessageToActivity(messenger, bundle);
			return;
		}			
        
		// send a msg to the activity to signal that we started the service:
        bundle.putString("type", "startService");
        SendMessageToActivity(messenger, bundle);
		
        // extract the params:
		int day = extras.getInt("day"); 
		int month = extras.getInt("month");
		int year = extras.getInt("year");
		int numNights = extras.getInt("numNights");
		boolean reqYos = extras.getBoolean("reqYos"); 
		boolean reqTM = extras.getBoolean("reqTM");
		
		Bundle availSites = new Bundle();
		
		// perform a generic yosemite campgrounds request if flag is set:
		if (reqYos)
			availSites.putAll(parseHtmlYos(yosRequest(m_requestUrlYos, m_requestArgsYos, day, month, year, numNights)));
		
		// if we're also checking for Tuolumne, do the specific TM request:
		if (reqTM)
			availSites.putAll(parseHtmlTM(yosRequest(m_requestUrlTM, m_requestArgsTM, day, month, year, numNights)));
		
		// send results msg to activity:
        bundle.putString("type", "results");
        bundle.putBundle("results", availSites);
        SendMessageToActivity(messenger, bundle);
	}
	
	public void SendMessageToActivity(Messenger messenger, Bundle bundle)
	{
		Message msg = new Message();
		msg.setData(bundle);
		try {
			messenger.send(msg);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public boolean isNetworkAvailable() 
	{
	    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo networkInfo = cm.getActiveNetworkInfo();
	    // if no network is available networkInfo will be null otherwise check if we are connected
	    if (networkInfo != null && networkInfo.isConnected())
	        return true;

	    return false;
	} 
	
	private String yosRequest(String strURL, String[][] requestArgs, int day, int month, int year, int numNights)
	{
		try
	   	{
			// TODO: 
			// - handle connection/request errors, retry if failed
			// - check why connection fails using 4G but works with WIFI
			
			URL url;
	    	HttpURLConnection conn;
	   		url = new URL(strURL);

	   		// build the request parameters string:
	   		StringBuilder params = new StringBuilder();
	   		for (int i=0; i<requestArgs.length; i++)
	   		{
	   			if (i > 0)
	   				params.append("&");
	   			
	   			params.append(requestArgs[i][0] + "=" + URLEncoder.encode(requestArgs[i][1], "UTF-8"));
	   		}
	   		
	   		// add the date param:
	   		GregorianCalendar date = new GregorianCalendar(year, month, day);
	   		SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd yyyy");
	   		params.append("&campingDate=" + URLEncoder.encode(sdf.format(date.getTime()).toString(), "UTF-8"));
	   		// add the length of stay param:
	   		params.append("&lengthOfStay=" +  numNights);
	   		
	   		conn = (HttpURLConnection) url.openConnection();
	    	// set the output to true to indicate we are outputting POST data:
	    	conn.setDoOutput(true);
	    	// once we set the output to true, we don't really need to set the request method to post, but do it anyway:
	    	conn.setRequestMethod("POST");

	    	// Android documentation suggested that you set the length of the data you are sending to the server, BUT
	    	// do NOT specify this length in the header by using conn.setRequestProperty("Content-Length", length)
	    	// use this instead:
	    	conn.setFixedLengthStreamingMode(params.toString().getBytes().length);
	    	conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
	    	// send the POST out
	    	PrintWriter out = new PrintWriter(conn.getOutputStream());
	    	out.print(params);
	    	out.close();
	    		    	
	    	// set the read timeout:
	    	conn.setReadTimeout(5000);
	    	
	    	// build the string to store the response text from the server
	    	StringBuilder response = new StringBuilder();
	    	// start listening to the stream
	    	Scanner inStream = new Scanner(conn.getInputStream());
	    	// process the stream and store it in StringBuilder
	    	while(inStream.hasNextLine())
	    		response.append(inStream.nextLine());
	    	
	    	return response.toString();
	    }
	    // catch some error
	    catch(MalformedURLException ex)
	    {  
	    	return ex.toString();
	    }
	    // and some more
	    catch(IOException ex)
	    {
	    	Log.e(TAG,Log.getStackTraceString(ex)); 
	    	return ex.toString();
	    }
		
		//return "uncaught error!";
	}
	
	// super ghetto parsing of the reservation results web page:
	protected Bundle parseHtmlYos(String strHtml)
	{
	  	int searchIndex = 0;
	  	Bundle results = new Bundle();
	  	
	  	// find all campgrounds:
	  	while ((searchIndex = strHtml.indexOf("site_types_title", searchIndex)) != -1)
	  	{
	  		// extract number of available sites for each campground:
	  		int availStart = strHtml.indexOf("<h2>", searchIndex);
	  		if (availStart == -1)
	  			continue;
	  		
	  		int availEnd = strHtml.indexOf("</h2>", availStart);
	  		if (availEnd == -1)
	  			continue;
	  		
	  		String strAvail = strHtml.substring(availStart+4, availEnd);
	  		// extract the number of available sites:
	  		int numAvail = Integer.parseInt(strAvail.substring(0, strAvail.indexOf(' ')));
	  		
	  		// now extract the campground's name:
	  		int nameStart = strHtml.lastIndexOf("facility_link", availStart);
	  		if (nameStart == -1)
	  			continue;
	  		nameStart = strHtml.indexOf("title=", nameStart);
	  		if (nameStart == -1)
	  			continue;
	  		nameStart += 7; // move the index beyond "title='"
	  		
	  		int nameEnd = strHtml.indexOf('\'', nameStart);
	  		if (nameEnd == -1)
	  			continue;
	  		
	  		String strCampName = strHtml.substring(nameStart, nameEnd);
	  		
	  		// update current search index:
	  		searchIndex = availEnd + 5;
	  		
	  		// skip if this is tuolumne meadows campground since we're treating as a special case:
	  		if (strCampName.equalsIgnoreCase("tuolumne meadows"))
	  			continue;
	  		
	  		// add campground to the results bundle if it has available sites:
  			if (numAvail > 0)
	  			results.putInt(strCampName, numAvail);
	  	}
	  	
	  	return results;
	}
	
	private String extractStringFromHtml(String str, String strStart, String strEnd, int index)
	{
	  	int start = str.indexOf(strStart, index);
	  	if (start == -1)
  			return "";
	  	int end = str.indexOf(strEnd, start);
  		if (end == -1)
  			return "";
  		
  		return str.substring(start + strStart.length(), end);
	}
	
	protected Bundle parseHtmlTM(String strHtml)
	{
	  	int searchIndex = 0;
	  	Bundle results = new Bundle();
	  	
	  	// This is code to extract the overall number of available sites for this campground:
	  	/*String strMatch = "<div class='matchSummary'>";
	  	String strAvail = extractStringFromHtml(strHtml, "<div class='matchSummary'>", "</div>", searchIndex);
	  	if (strAvail.isEmpty())
	  		return results;
  		// extract the number of available sites:
  		int numAvail = Integer.parseInt(strAvail.substring(0, strAvail.indexOf(' ')));*/
	  	
	  	// For tuolumne, we're not interested in the group or equestrian sites
	  	// so we want to check specifically for the STANDARD NON ELECTRIC and TENT ONLY NONELECTRIC sites:
	  	
	  	// extract the info for the Standard sites:
	  	// this is super ghetto, but it works :)
	  	String strAvail = extractStringFromHtml(strHtml, "STANDARD NONELECTRIC (", ")", searchIndex);
  		// extract the number of available sites:
  		int numAvailStandard = Integer.parseInt(strAvail);
  		
  		// extract the info for the Tent only sites:
	  	// ghetto, ghetto, ghetto!!
	  	strAvail = extractStringFromHtml(strHtml, "TENT ONLY NONELECTRIC (", ")", searchIndex);
  		// extract the number of available sites:
  		int numAvailTentOnly = Integer.parseInt(strAvail);
  		
  		// add to results bundle if we have sites available in that category:
  		if (numAvailStandard + numAvailTentOnly > 0)
  	  		results.putInt("Tuolumne Meadows", numAvailStandard + numAvailTentOnly);
	  	
  		return results;
	}	
  
}