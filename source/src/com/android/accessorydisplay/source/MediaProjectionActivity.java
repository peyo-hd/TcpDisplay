package com.android.accessorydisplay.source;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.accessorydisplay.common.Logger;

import java.net.Socket;

public class MediaProjectionActivity extends Activity {
    private static final String TAG = "MediaProjectionActivity";

    private TextView mLogTextView;
	private EditText mAddressText;
	private Button mButton;
    private Logger mLogger;

    private boolean mConnected;
    private SourceTcpTransport mTransport;

    private MediaProjectionService mMediaProjectionService;

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection = null;

    private static final int PERMISSION_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.source_activity);
        mLogTextView = (TextView) findViewById(R.id.logTextView);
		mAddressText = (EditText)findViewById(R.id.address);
		mButton = (Button)findViewById(R.id.button);

        mLogTextView.setMovementMethod(ScrollingMovementMethod.getInstance());
        mLogger = new TextLogger();

        mProjectionManager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged()");
    }

    private MediaProjection.Callback mMediaProjectionCallback =
        new MediaProjection.Callback() {
            @Override
            public void onStop() {
                disconnect();
            }
        };

    @Override
    protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    public void onConnectClicked(View view) {
        if (!mConnected) {
            mSinkAddress = mAddressText.getText().toString();
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), PERMISSION_CODE);
        } else {
            disconnect();
        }
    }

    private String mSinkAddress = null;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            return;
        }

        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        if (mMediaProjection != null) {
            mMediaProjection.registerCallback(mMediaProjectionCallback, null);
            connect();
        }
    }

    private void connect() {
        if (mMediaProjection != null) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        final Socket socket = new Socket(mSinkAddress, 1234);
                        MediaProjectionActivity.this.runOnUiThread(new Runnable() {
                            public void run() {
                                mTransport = new SourceTcpTransport(mLogger, socket);
                                mButton.setText(R.string.button_disconnect);
                                startServices();
                                mTransport.startReading();
                                mLogger.log("Connected.");
                                mConnected = true;
                            }
                        });
                    } catch (Exception e) {
                        mLogger.log("Socket connection error");
                    }
                    return null;
                }
            }.execute();
        }
    }

    private void disconnect() {
        if (!mConnected) return;
        mLogger.log("Disconnecting from TCP sink");
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                MediaProjectionActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        stopServices();
                    }
                });
                if (mTransport != null) {
                    mTransport.close();
                    mTransport = null;
                }
                mMediaProjection = null;
                MediaProjectionActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        mConnected = false;
                        mButton.setText(R.string.button_connect);
                    }
                });
                return null;
            }
        }.execute();
    }

    private void startServices() {
        if (mMediaProjection != null) {
            mMediaProjectionService = new MediaProjectionService(this, mTransport, mMediaProjection);
            mMediaProjectionService.start();
        }
    }

    private void stopServices() {
        if (mMediaProjectionService != null) {
            mMediaProjectionService.stop();
            mMediaProjectionService = null;
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
