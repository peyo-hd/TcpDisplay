package com.android.accessorydisplay.source;

import com.android.accessorydisplay.common.Logger;
import com.android.accessorydisplay.common.Transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Sends or receives messages over TCP connection
 */
public class SourceTcpTransport extends Transport {
    private Socket mSocket;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private Object lock;

    public SourceTcpTransport(Logger logger, Socket socket) {
        super(logger, 256 * 1024);
        try {
			mInputStream = socket.getInputStream();
	        mOutputStream = socket.getOutputStream();
		} catch (IOException e) {
	        mInputStream = null;
	        mOutputStream = null;
			e.printStackTrace();
			return;
		}
        mSocket = socket;
        lock = new Object();
    }

    @Override
    protected void ioClose() {
    	if (mSocket != null) {
    		try {
				mSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
        mInputStream = null;
        mOutputStream = null;
    }

    @Override
    protected int ioRead(byte[] buffer, int offset, int count) throws IOException {
        if (mInputStream == null) {
            throw new IOException("Stream was closed.");
        }
        return mInputStream.read(buffer, offset, count);
    }

    @Override
    protected void ioWrite(byte[] buffer, int offset, int count) throws IOException {
        if (mOutputStream == null) {
            throw new IOException("Stream was closed.");
        }
        final byte[] buf = buffer.clone();
        final int off = offset;
        final int cnt = count;
        new Thread() {
			@Override
			public void run() {
				synchronized (lock) {
					try {
						mOutputStream.write(buf, off, cnt);
						mOutputStream.flush();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
        }.start();
    }
}
