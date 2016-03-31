package com.android.accessorydisplay.sink;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;

import com.android.accessorydisplay.common.Logger;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.TextView;

public class SinkTcpActivity extends Activity {
    private static final String TAG = "SinkTcpActivity";

    private TextView mLogTextView;
    private SurfaceView mSurfaceView;
    private Logger mLogger;

    private boolean mConnected;
    private SinkTcpTransport mTransport;

    private boolean mAttached;
    private DisplaySinkService mDisplaySinkService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sink_activity);

        mLogTextView = (TextView) findViewById(R.id.logTextView);
        mLogTextView.setMovementMethod(ScrollingMovementMethod.getInstance());
        mLogger = new TextLogger();

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
    }

	@Override
	protected void onResume() {
		super.onResume();
        mLogger.log("IP Address : " + getDefaultIpAddress());
        mLogger.log("Waiting for accessory display source to be connected via TCP...");
		try {
			mServerSocket = new ServerSocket(1234);
	        mAcceptor.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
    private String getDefaultIpAddress() {
    	ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    	int activeType = cm.getActiveNetworkInfo().getType();
    	Network[] networks = cm.getAllNetworks();
        for (int i = 0; i < networks.length; i++) {
            if (activeType == cm.getNetworkInfo(networks[i]).getType())
            	return formatIpAddress(cm.getLinkProperties(networks[i]));
        }
        return null;
    }
    
    private String formatIpAddress(LinkProperties prop) {
        if (prop == null) return null;
        Iterator<LinkAddress> iter = prop.getLinkAddresses().iterator();
        while(iter.hasNext()) {
        	InetAddress iaddr = iter.next().getAddress();
        	if (iaddr instanceof Inet4Address) {
        		return iaddr.getHostAddress();
        	}
        }
        return null;
    }

	
    @Override
	protected void onPause() {
  		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
		        if (mConnected) disconnect();
		        try {
		            if (mServerSocket != null) mServerSocket.close();
		        } catch (IOException e) {
					e.printStackTrace();
		        }
				return null;
			}
  		}.execute();
        try {
			mAcceptor.join(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		super.onPause();
	}
     
    ServerSocket mServerSocket;
    Thread mAcceptor = new Thread() {
        @Override
        public void run() {
            while (true) {
                Socket client = null;
                try {
                    client = mServerSocket.accept();
                } catch (SocketException e) {
                	mLogger.log("ServerSocket was closed, exiting");
                    break;
                } catch (IOException e) {
                    mLogger.log("accept() caught exception");
                    continue;
                }
                if (client != null) {
                	connect(client);
                }
            }
        }    	
    };
    
    private void connect(final Socket socket) {
        mLogger.log("Connecting to TCP source");
        if (mConnected) disconnect();
		SinkTcpActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mTransport = new SinkTcpTransport(mLogger, socket);
				mConnected = true;
				startServices();
				mTransport.startReading();
			}
		});
    }

    private void disconnect() {
        mLogger.log("Disconnecting from TCP source");
        stopServices();
        mConnected = false;
        if (mTransport != null) {
            mTransport.close();
            mTransport = null;
        }
    }

    private void startServices() {
        mDisplaySinkService = new DisplaySinkService(this, mTransport,
                getResources().getConfiguration().densityDpi);
        mDisplaySinkService.start();

        if (mAttached) {
            mDisplaySinkService.setSurfaceView(mSurfaceView);
        }
    }

    private void stopServices() {
        if (mAttached) {
            mDisplaySinkService.setSurfaceView(null);
        }
        
        if (mDisplaySinkService != null) {
            mDisplaySinkService.stop();
            mDisplaySinkService = null;
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        if (mDisplaySinkService != null) {
            mDisplaySinkService.setSurfaceView(mSurfaceView);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAttached = false;
        if (mDisplaySinkService != null) {
            mDisplaySinkService.setSurfaceView(null);
        }
    }

    class TextLogger extends Logger {
        @Override
        public void log(final String message) {
            Log.d(TAG, message);
            mLogTextView.post(new Runnable() {
                @Override
                public void run() {
                    mLogTextView.append(message);
                    mLogTextView.append("\n");
                }
            });
        }
    }

}
