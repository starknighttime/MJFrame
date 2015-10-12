package com.motivejelly.frame;

import com.motivejelly.supportlibary.AdsList;
import com.motivejelly.supportlibary.Advertisement;
import com.motivejelly.supportlibary.MsgType;
import com.gamingbeaststudio.developtoolkit.network.MessageDao;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.VideoView;

public class FrameActivity extends Activity {

	ImageView adImage;
	VideoView adVideo;
	ImageView adQR;
	TextView notice;
	AdsList ads;
	int curAd = 0;
	int lastAd;
	int noticing = Constants.NONOTICE;
	int countDown = 0;
	boolean running = true;

	FrameController controller;

	@SuppressLint("HandlerLeak")
	public Handler uiHandler = new Handler() {

		@Override
		public void handleMessage(final Message msg) {

			switch (msg.what) {
			case MsgType.F2F_ADS_UPDATED:
				refreshAds();
				break;
			case MsgType.F2F_ADSLIST_UPDATED:
				controller.updateAds();
				break;
			case MsgType.N2F_ONLINE:
				controller.online();
				break;
			case MsgType.N2F_REQUEST_LOG:
				controller.uploadLog();
				break;
			case MsgType.N2F_SERVICE_ANSWER:
				serviceAnswered();
				break;
			case MsgType.N2F_SERVICE_CALLED:
				serviceCalled();
				break;
			case MsgType.N2F_SET_NAME:
				controller.setFrameId((MessageDao) msg.obj);
				break;
			case MsgType.N2F_SLEEP:
				frameWake(false);
				break;
			case MsgType.N2F_UPDATE_ADSLIST:
				controller.updateAdsList((MessageDao) msg.obj);
				break;
			case MsgType.N2F_WAKE:
				frameWake(true);
				break;
			}
		}
	};
	Runnable displayAd = new Runnable() {

		@Override
		public void run() {

			if (running) {
				final Advertisement ad = ads.get(curAd++);
				ad.display();
				if (curAd == lastAd) {
					curAd = 0;
				}
				adImage.setVisibility(View.GONE);
				adVideo.setVisibility(View.GONE);
				adQR.setVisibility(View.GONE);

				final String adPath = Constants.PACKAGE_NAME + "ads/"
						+ ad.getSn();

				if (ad.getSn().endsWith(".jpg")) {
					adImage.setImageBitmap(BitmapFactory.decodeFile(adPath));
					adImage.setVisibility(View.VISIBLE);
				} else {
					adVideo.setVideoPath(adPath);
					running = false;
					adVideo.start();
					adVideo.setVisibility(View.VISIBLE);
				}

				if (ad.getQrsn().length() != 0) {
					final String qrPath = Constants.PACKAGE_NAME + "qr/"
							+ ad.getQrsn();
					final RelativeLayout.LayoutParams rl = new RelativeLayout.LayoutParams(
							adQR.getLayoutParams());
					rl.setMargins(ad.getQrposx(), ad.getQrposy(), 0, 0);
					adQR.setLayoutParams(rl);
					adQR.setImageBitmap(BitmapFactory.decodeFile(qrPath));
					adQR.setVisibility(View.VISIBLE);
				}
				uiHandler.postDelayed(this, ad.getDuration() * 1000);
			}
		}
	};
	Runnable displayNotice = new Runnable() {

		@Override
		public void run() {

			countDown++;
			notice.setText(Constants.NOTICES[noticing]);
			notice.setVisibility(View.VISIBLE);
			notice.requestFocus();
			uiHandler.postDelayed(hideNotice, 5000);
		}
	};
	Runnable hideNotice = new Runnable() {

		@Override
		public void run() {

			if (--countDown == 0) {
				notice.setText("");
				notice.setVisibility(View.GONE);
				notice.clearFocus();
				noticing = -1;
			}
		}
	};

	// TODO Runtime
	@Override
	protected void onCreate(final Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		updateApplication();
		initComponent();
		startDisplay();
	}

	@Override
	protected void onResume() {

		super.onResume();
		running = true;
	}

	@Override
	protected void onPause() {

		super.onPause();
		running = false;
	}

	// TODO Update
	private void updateApplication() {

	}

	// TODO Initialize Component
	private void initComponent() {

		controller = new FrameController(getSharedPreferences("share",
				MODE_PRIVATE),
				(WifiManager) getSystemService(Context.WIFI_SERVICE),
				uiHandler, this);

		setContentView(R.layout.activity_frame);
		adImage = (ImageView) findViewById(R.id.iv_ad);
		adVideo = (VideoView) findViewById(R.id.vv_ad);
		adVideo.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

			@Override
			public void onCompletion(final MediaPlayer mp) {

				running = true;
				uiHandler.post(displayAd);
			}
		});
		adQR = (ImageView) findViewById(R.id.iv_qr);
		adQR.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(final View v) {

				if (noticing == Constants.NONOTICE) {
					noticing = Constants.CALLSERVICE;
					uiHandler.post(displayNotice);
					controller.callService();
				}
			}
		});
		notice = (TextView) findViewById(R.id.tv_notice);
	}

	// TODO Display Advertisement
	private void startDisplay() {

		ads = controller.getAdsList();
		lastAd = ads.size();
		if (curAd >= lastAd) {
			curAd = 0;
		}
		running = true;
		uiHandler.post(displayAd);
		controller.online();
	}

	private void stopDisplay() {

		running = false;
		uiHandler.post(displayAd);
		controller.writeLog(ads);
	}

	private void refreshAds() {

		stopDisplay();
		controller.refreshAds();
		startDisplay();
	}

	private void serviceCalled() {

		noticing = Constants.SERVICECALLED;
		uiHandler.post(displayNotice);
	}

	private void serviceAnswered() {
		
		noticing = Constants.SERVICEANSWERED;
		uiHandler.post(displayNotice);
	}

	private void frameWake(final boolean isWake) {

		final WindowManager.LayoutParams params = getWindow().getAttributes();
		params.flags |= LayoutParams.FLAG_KEEP_SCREEN_ON;
		params.screenBrightness = isWake ? 1.0f : 0.0f;
		getWindow().setAttributes(params);
		if (isWake) {
			startDisplay();
		} else {
			stopDisplay();
		}
	}

}