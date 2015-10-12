package com.motivejelly.frame;

import java.io.File;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import com.gamingbeaststudio.developtoolkit.network.MessageDao;
import com.gamingbeaststudio.developtoolkit.network.TcpTools;
import com.gamingbeaststudio.developtoolkit.network.UdpTools;
import com.gamingbeaststudio.developtoolkit.tools.Tools;
import com.motivejelly.supportlibary.AdsList;
import com.motivejelly.supportlibary.Advertisement;
import com.motivejelly.supportlibary.FrameInfo;
import com.motivejelly.supportlibary.MsgType;
import com.motivejelly.supportlibary.Ports;
import com.motivejelly.supportlibary.Support;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

public class FrameController {

	Activity main;
	String nodeIp = "255.255.255.255";
	String frameIp;
	String frameId;
	String adsListVersion;
	String frameMac;
	List<String> missingFiles;
	List<Advertisement> displayLog = new ArrayList<Advertisement>();
	int fileCursor;
	boolean isUpdating = false;

	SharedPreferences sp;
	Editor spE;
	WifiManager wm;
	Handler uiHandler;
	Handler fileHandler;
	HandlerThread ht;

	public FrameController(final SharedPreferences sp, final WifiManager wm,
			final Handler uiHandler, final Activity main) {

		this.uiHandler = uiHandler;
		this.sp = sp;
		this.wm = wm;
		this.main = main;
		spE = sp.edit();
		initController();
		activateReceiver();
	}

	private void initController() {

		isFirstRun();
		frameIp = Tools.intToIp(wm.getConnectionInfo().getIpAddress());
		frameId = sp.getString("frameId", Constants.DEFAULT_FRAME_ID);
		adsListVersion = sp.getString("AdsListVersion",
				Constants.DEFAULT_ADSLIST_VERSION);
		frameMac = wm.getConnectionInfo().getMacAddress();
	}

	private void isFirstRun() {

		if (sp.getBoolean("isFirstRun", true)) {
			initResources();
			spE.putBoolean("isFirstRun", false);
			spE.commit();
		}
	}

	private void initResources() {

		int t = Constants.DEFAULT_ADS.length;
		for (int i = 0; i < t; i++) {
			final String filePath = Constants.PACKAGE_NAME + "ads/";
			Tools.writeFileFromAssets(Constants.DEFAULT_ADS[i], filePath, main);
		}
		t = Constants.DEFAULT_QR.length;
		for (int i = 0; i < t; i++) {
			final String filePath = Constants.PACKAGE_NAME + "qr/";
			Tools.writeFileFromAssets(Constants.DEFAULT_QR[i], filePath, main);
		}
		final String filePath = Constants.PACKAGE_NAME;
		Tools.writeFileFromAssets("ads_list.json", filePath, main);
	}

	private void activateReceiver() {

		final Thread thread = new Thread() {

			public void run() {

				while (true) {
					try {
						final MessageDao msgD = UdpTools
								.receive(Ports.FRAME_UDP_RECEIVE);
						final Message msg = new Message();
						msg.what = Integer.parseInt(msgD.getMsgType());
						msg.obj = msgD;
						uiHandler.sendMessage(msg);
					} catch (final Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
		thread.setDaemon(true);
		thread.start();
	}

	private void requestFile(final String fileName) {

		final byte[] data = Tools.toByteArray(new MessageDao(frameIp, String
				.valueOf(MsgType.F2N_REQUEST_FILE), fileName));
		UdpTools.send(Ports.FRAME_UDP_SEND, data, data.length, nodeIp,
				Ports.NODE_UDP_RECEIVE);
	}

	// TODO Services
	public void online() {

		final byte[] data = Tools.toByteArray(new MessageDao(frameIp, String
				.valueOf(MsgType.F2N_ONLINE), new FrameInfo(frameMac, frameId,
				adsListVersion, frameIp)));
		UdpTools.send(Ports.FRAME_UDP_SEND, data, data.length, nodeIp,
				Ports.NODE_UDP_RECEIVE);
	}

	public void callService() {

		final byte[] data = Tools.toByteArray(new MessageDao(frameIp, String
				.valueOf(MsgType.F2N_CALL_SERVICE), new FrameInfo(frameMac,
				frameId, adsListVersion, frameIp)));
		UdpTools.send(Ports.FRAME_UDP_SEND, data, data.length, nodeIp,
				Ports.NODE_UDP_RECEIVE);
	}

	public void updateAdsList(final MessageDao msg) {

		nodeIp = msg.getSendUserIp();
		if (msg.getBody() == null) {
			return;
		}
		if (!isUpdating) {
			final Thread fileThread = new Thread() {

				public void run() {

					try {
						TcpTools.openServer(Ports.FRAME_TCP_RECEIVE,
								Constants.PACKAGE_NAME, "ads_list_new.json");
						uiHandler.sendEmptyMessage(MsgType.F2F_ADSLIST_UPDATED);
					} catch (final Exception e) {
						if (e instanceof SocketTimeoutException) {
							try {
								Thread.sleep((long) (Math.random() * 5));
							} catch (InterruptedException e1) {
								e1.printStackTrace();
							}
							final Message msgD = new Message();
							msgD.what = Integer.parseInt(msg.getMsgType());
							msgD.obj = msg;
							uiHandler.sendMessage(msgD);
						} else {
							e.printStackTrace();
							return;
						}
					}
				}
			};
			fileThread.setDaemon(true);
			fileThread.start();
			requestFile("ads_list_" + (String) msg.getBody() + ".json");
		}
	}

	public void updateAds() {

		isUpdating = true;
		missingFiles = Support.checkFiles(Constants.PACKAGE_NAME,
				"ads_list_new.json");
		fileCursor = 0;

		if (missingFiles.size() != 0) {
			ht = new HandlerThread("File Thread");
			ht.setDaemon(true);
			ht.start();
			fileHandler = new Handler(ht.getLooper()) {

				@Override
				public void handleMessage(final Message msg) {

					switch (msg.what) {
					case Constants.TRANSFERING:
						final Thread fileThread = new Thread() {
							public void run() {
								final String fileName = missingFiles
										.get(fileCursor);
								final String filePath = fileName
										.startsWith("ad") ? "ads/" : "qr/";
								try {
									TcpTools.openServer(
											Ports.FRAME_TCP_RECEIVE,
											Constants.PACKAGE_NAME + filePath,
											fileName);
									fileCursor++;
								} catch (final Exception e) {
									e.printStackTrace();
									if (e instanceof SocketTimeoutException) {
										try {
											Thread.sleep((long) (Math.random() * 5));
										} catch (InterruptedException e1) {
											e1.printStackTrace();
										}
									}
								} finally {
									fileHandler
											.sendEmptyMessage(Constants.NEXT);
								}
							}
						};
						fileThread.setDaemon(true);
						fileThread.start();
						requestFile(missingFiles.get(fileCursor));
						break;
					case Constants.NEXT:
						if (fileCursor < missingFiles.size()) {
							fileHandler.sendEmptyMessage(Constants.TRANSFERING);
						} else {
							missingFiles = Support
									.checkFiles(Constants.PACKAGE_NAME,
											"ads_list_new.json");
							updateAds();
						}
						break;
					}
				}
			};
			fileHandler.sendEmptyMessage(Constants.TRANSFERING);
		} else {
			uiHandler.sendEmptyMessage(MsgType.F2F_ADS_UPDATED);
			isUpdating = false;
		}
	}

	public void refreshAds() {

		final File adsListJson = new File(Constants.PACKAGE_NAME
				+ "ads_list.json");
		new File(Constants.PACKAGE_NAME + "ads_list_new.json")
				.renameTo(adsListJson);
	}

	public void setFrameId(final MessageDao msg) {

		frameId = (String) msg.getBody();
		spE.putString("frameId", frameId);
		spE.commit();
	}

	public AdsList getAdsList() {

		final AdsList ads = Support.getAdsListFromJson(Constants.PACKAGE_NAME
				+ "ads_list.json");
		adsListVersion = ads.getVersion();
		spE.putString("AdsListVersion", adsListVersion);
		spE.commit();
		return ads;
	}

	public void writeLog(AdsList ads) {

		int j = ads.size();
		for (int i = 0; i < j; i++) {
			Advertisement ad = ads.get(i);
			if (ad.getSn().startsWith("addf")) {
				continue;
			}
			if (displayLog.contains(ad)) {
				displayLog.get(displayLog.indexOf(ad)).sumCount(ad);
			} else {
				displayLog.add(new Advertisement(ad.getSn(), ad.getDuration(),
						ad.getCount()));
			}
		}
	}

	public void uploadLog() {

	}
}