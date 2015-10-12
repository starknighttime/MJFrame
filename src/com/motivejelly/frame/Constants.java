package com.motivejelly.frame;

import android.os.Environment;

public class Constants {

	public static final String PACKAGE_NAME = Environment
			.getExternalStorageDirectory().getAbsolutePath()
			+ "/Android/data/com.motivejelly.frame/";
	public static final String DEFAULT_FRAME_ID = "Not Assigned";
	public static final String DEFAULT_ADSLIST_VERSION = "20151001";
	public static final String[] DEFAULT_ADS = { "addf09252015000001.jpg",
			"addf09252015000002.mp4", "addf09252015000003.jpg" };
	public static final String[] DEFAULT_QR = { "qrdf09252015000001.jpg" };
	
	public static final String[] NOTICES = {"Seeking Node","Service Called Please Wait......","Waiter/Waitress is Coming"};
	public static final int NONOTICE = -1;
	public static final int CALLSERVICE = 0;
	public static final int SERVICECALLED = 1;
	public static final int SERVICEANSWERED = 2;
	
	// Controller Running Status
	public static final int TRANSFERING = 1;
	public static final int NEXT = 2;
	
	public static final int NOTICE_DISPLAY_TIME = 5;
}