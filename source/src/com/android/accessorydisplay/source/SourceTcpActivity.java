package com.android.accessorydisplay.source;

import java.net.Socket;

import com.android.accessorydisplay.common.Logger;
import com.android.accessorydisplay.source.presentation.DemoPresentation;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class SourceTcpActivity extends Activity {
    private static final String TAG = "SourceActivity";

    private TextView mLogTextView;
	private EditText mAddressText;
	private Button mButton;
    private Logger mLogger;

    private boolean mConnected;
    private SourceTcpTransport mTransport;

    private DisplaySourceService mDisplaySourceService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.source_activity);

        mLogTextView = (TextView) findViewById(R.id.logTextView);
		mAddressText = (EditText)findViewById(R.id.address);
		mButton = (Button)findViewById(R.id.button);
        mLogTextView.setMovementMethod(ScrollingMovementMethod.getInstance());
        mLogger = new TextLogger();
    }

	public void onConnectClicked(View view) {
        if (!mConnected) {
            connect(mAddressText.getText().toString());
        } else {
            disconnect();
        }
	}

    @Override
    protected void onPause() {
        disconnect();
        super.onPause();
    }

    private void connect(final String address) {
        mLogger.log("Connecting to TCP sink");
        if (mConnected) {
            disconnect();
        }
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    final Socket socket = new Socket(address, 1234);
                    SourceTcpActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            mTransport = new SourceTcpTransport(mLogger, socket);
                            mLogger.log("Connected.");
                            mConnected = true;
                            mButton.setText(R.string.button_disconnect);
                            startServices();
                            mTransport.startReading();
                        }
                    });
                } catch (Exception e) {
                    mLogger.log("Socket connection error");
                }
                return null;
            }
        }.execute();
    }

    private void disconnect() {
        if (!mConnected) return; 
        mLogger.log("Disconnecting from TCP sink");
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                SourceTcpActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        stopServices();
                        mConnected = false;
                        mButton.setText(R.string.button_connect);
                    }
                });
                if (mTransport != null) {
                    mTransport.close();
                    mTransport = null;
                }
                return null;
            }
        }.execute();
    }

    private void startServices() {
        mDisplaySourceService = new DisplaySourceService(this, mTransport, new Presenter());
        mDisplaySourceService.start();
    }

    private void stopServices() {
        if (mDisplaySourceService != null) {
            mDisplaySourceService.stop();
            mDisplaySourceService = null;
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

    class Presenter implements DisplaySourceService.Callbacks {
        private DemoPresentation mPresentation;

        @Override
        public void onDisplayAdded(Display display) {
            mLogger.log("TCP display added");
            mPresentation = new DemoPresentation(SourceTcpActivity.this, display, mLogger);
            mPresentation.show();
        }

        @Override
        public void onDisplayRemoved(Display display) {
            mLogger.log("TCP display removed: ");

            if (mPresentation != null) {
                mPresentation.dismiss();
                mPresentation = null;
            }
        }
    }
}
